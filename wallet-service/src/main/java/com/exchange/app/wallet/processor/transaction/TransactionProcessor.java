package com.exchange.app.wallet.processor.transaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.wallet.cronjob.outbox.constant.OutboxConstant;
import com.exchange.app.wallet.dao.manager.*;
import com.exchange.app.wallet.exception.InvalidEnumException;
import com.exchange.app.wallet.kafka.producer.DefaultPublisher;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletBucket;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.po.wallet.WalletAccountMapping;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.*;
import com.exchange.common.db.utils.DbTransactionHelper;
import com.exchange.common.outbox.dao.manager.OutboxManager;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.time.LocalDateTimeHelper;
import com.exchange.proto.ledger.common.LedgerDirectionPb;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.proto.wallet.common.BusinessTypePb;
import com.exchange.proto.wallet.common.OperationTypePb;
import com.exchange.proto.wallet.common.ServiceIdPb;
import com.exchange.proto.wallet.wallet.TransactionLinePb;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TransactionProcessor {
    private final TransactionTemplate transactionTemplate;

    private final BalanceSnapshotManager balanceSnapshotManager;
    private final WalletReservationManager walletReservationManager;
    private final WalletTransactionManager walletTransactionManager;
    private final WalletActionManager walletActionManager;
    private final WalletAccountMappingManager walletAccountMappingManager;
    private final OutboxManager outboxManager;
    private final DefaultPublisher postLedgerPublisher;

    // reserve -> snapshot: -available, +reserved
    // consume -> reservation: -remaining, +pending_settle
    // release -> snapshot, reservation: -reserved, +available | -pending_settle, +released, change status
    // package private
    Result<TransactionInfo> beforePostingLedger(String referenceId, ServiceIdPb serviceIdPb, String idempotenceKey, BusinessTypePb businessTypePb, TransactionType transactionType, boolean needPostLedger, List<TransactionLinePb> linePbs) {
        Result<WalletTransaction> idempotenceResult = ifExists(serviceIdPb, idempotenceKey);
        if (idempotenceResult.isFailed()) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid service id " + serviceIdPb);
        }
        if (idempotenceResult.value != null) {
            log.info("wallet txn already exists: {}", idempotenceResult.value);
            return Results.success(TransactionInfo.create(idempotenceResult.value));
        }

        Result<WalletTransaction> txnResult = validateReqAndCreateTxn(referenceId, serviceIdPb, idempotenceKey, businessTypePb, transactionType, needPostLedger, linePbs);
        if (txnResult.isFailed()) {
            return Results.fail(txnResult);
        }
        WalletTransaction walletTxn = txnResult.value;

        // complete txn immediately if no need to post ledger
        if (!needPostLedger) {
            walletTxn.setTxnStatus(TransactionStatus.COMPLETED);
        }

        // get snapshot from db
        Set<String> walletRefs = linePbs.stream().distinct().map(TransactionLinePb::getWalletRef).collect(Collectors.toSet());
        List<BalanceSnapshot> balanceSnapshots = balanceSnapshotManager.selectByRefs(walletTxn.getInitiator(), new ArrayList<>(walletRefs));
        Map<String, BalanceSnapshot> walletRefToSnapshot = balanceSnapshots.stream().collect(Collectors.toMap(BalanceSnapshot::getWalletReferenceId, snapshot -> snapshot));

        // get reservation from db
        Set<String> reservationRefs = linePbs.stream()
                .filter((line)-> !Strings.isEmpty(line.getReservationRef()))
                .distinct().map(TransactionLinePb::getReservationRef).collect(Collectors.toSet());
        List<WalletReservation> reservationsInRequest = new ArrayList<>();
        if (!reservationRefs.isEmpty()) {
            reservationsInRequest = walletReservationManager.selectByRefs(new ArrayList<>(reservationRefs));
            if (reservationsInRequest.size() != reservationRefs.size()) {
                log.error("reservations not found, requested_reservation: {}, reservation_in_db: {}", reservationRefs, reservationsInRequest);
                return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservations not found");
            }
        }
        Map<String, WalletReservation> refToReservation = reservationsInRequest.stream().collect(Collectors.toMap(WalletReservation::getReferenceId, reservation->reservation));

        // create action and reservation
        List<WalletAction> actions = new ArrayList<>();
        List<WalletReservation> createdReservations = new ArrayList<>();
        for (TransactionLinePb line : linePbs) {
            BalanceSnapshot snapshot = walletRefToSnapshot.get(line.getWalletRef());
            if (snapshot == null) {
                log.error("snapshot not found, line: {}", line);
                return Results.fail(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "snapshot not found");
            }

            WalletReservation reservation = null;
            switch (line.getOperationType()) {
                case OperationTypePb_Reserve:
                case OperationTypePb_Debit:
                    reservation = createReservation(walletTxn, snapshot.getWalletId(), line);
                    createdReservations.add(reservation);
                    refToReservation.put(reservation.getReferenceId(), reservation);
                    break;
                case OperationTypePb_Earmark:
                case OperationTypePb_Release:
                    reservation = refToReservation.get(line.getReservationRef());
                    if (reservation == null) {
                        log.error("reservation not found, ref: {}", line);
                        return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation not found");
                    }
            }

            String reservationId = null;
            if (reservation != null) {
                reservationId = reservation.getReferenceId();
            }
            List<WalletAction> actionsFromLine = createActionsFromLine(walletTxn.getTxnId(), snapshot.getWalletId(), reservationId, line);
            actions.addAll(actionsFromLine);
        }

        //validate
        Result<Void> result = validateActionInfo(actions, balanceSnapshots, new ArrayList<>(refToReservation.values()));
        if (result.isFailed()) {
            log.error("validate action info failed, result: {}", result);
            return Results.fail(result);
        }

        Outbox outbox;
        if (needPostLedger) {
            Result<Outbox> outboxResult = createOutbox(walletTxn, actions);
            if (outboxResult.isFailed()) {
                return Results.fail(outboxResult);
            }
            outbox = outboxResult.value;
        } else {
            outbox = null;
        }

        result = updateDbToCreateTxn(walletTxn, actions, balanceSnapshots, reservationsInRequest, createdReservations, outbox);
        if (result.isFailed()) {
            log.error("updateDbToCreateTxn failed: {}", result);
            return Results.fail(result);
        }

        if (needPostLedger) {
            Result<Void> publishResult = postLedgerPublisher.publish(outbox);
            if (publishResult.success) {
                if (outboxManager.updateStatusToFinalize(outbox, OutboxStatus.SENT, outbox.getLastAttemptAt())
                        == 1) {
                    outbox.setOutboxStatus(OutboxStatus.SENT);
                    outbox.setFinalizedAt(outbox.getLastAttemptAt());
                } else {
                    // do not block main flow
                    log.error("finalize outbox failed, {}", outbox);
                }
            } else {
                // publish failed should not block main flow, and should return txn execute successfully
                log.error("post ledger publish failed, outbox: {}, result: {}", outbox, publishResult);
            }
        }


        // TODO this part can be removed, reservation are queried from db after receiving reply from ledger
        // fill PK into created reservations
//        var reservations = walletReservationManager.selectByTxnId(walletTxn.getTxnId());
//        if (reservations.size() != createdReservations.size()) {
//            log.error("wallet_reservation_not_found, reservationsFromDb: {}, createdReservations: {}", reservations, createdReservations);
//            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "wallet_reservation_not_found");
//        }

        return Results.success(TransactionInfo.create(walletTxn, actions, new ArrayList<>(refToReservation.values()), balanceSnapshots));
    }

    private Result<WalletTransaction> ifExists(ServiceIdPb serviceIdPb, String idempotencyKey) {
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(serviceIdPb);
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            log.error("serviceId not found, serviceId: {}", serviceIdPb);
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid service id: " + serviceIdPb);
        }
        WalletTransaction walletTxn = walletTransactionManager.selectByIdempotencyKey(serviceId, idempotencyKey);
        return Results.success(walletTxn);
    }

    private Result<WalletTransaction> validateReqAndCreateTxn(String referenceId, ServiceIdPb serviceIdPb, String idempotenceKey, BusinessTypePb businessTypePb, TransactionType transactionType, boolean needPostLedger, List<TransactionLinePb> linePbs) {
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(serviceIdPb);
        BusinessType businessType = EnumPbMappers.businessTypePbMapper.to(businessTypePb);
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid service id: " + serviceIdPb);
        }
        if (businessType == null || businessType == BusinessType.UNKNOWN) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid business type: " + businessTypePb);
        }
        if (transactionType == null || transactionType == TransactionType.UNKNOWN) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid transaction type: " + transactionType);
        }
        for (TransactionLinePb line : linePbs) {
            // validate action type and amount
            if (line.getOperationType() == OperationTypePb.UNRECOGNIZED || line.getOperationType() == OperationTypePb.OperationTypePb_Unknown) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid operation type: " + line);
            }
            if (line.getAmount() <= 0) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid amount: " + line);
            }
        }

        WalletTransaction txn = createTransaction(transactionType, referenceId, serviceId, idempotenceKey, businessType);
        return Results.success(txn);
    }

    private Result<Void> validateActionInfo(List<WalletAction> actions, List<BalanceSnapshot> snapshots, List<WalletReservation> reservations) {
        Map<String, BalanceSnapshot> walletIdToSnapshot = Objects.requireNonNullElse(snapshots, new ArrayList<BalanceSnapshot>()).stream()
                .collect(Collectors.toMap(BalanceSnapshot::getWalletId, snapshot -> snapshot));
        Map<String, WalletReservation> walletIdToReservation = Objects.requireNonNullElse(reservations, new ArrayList<WalletReservation>()).stream()
                .collect(Collectors.toMap(WalletReservation::getReferenceId, reservation->reservation));

        if (actions == null || actions.isEmpty()) {
            log.error("empty actions list");
            return Results.fail(ErrorCode.WALLET_ACTION_NOT_FOUND, "empty actions list");
        }

        Result<Void> result = validateAssetInfo(actions);
        if (result.isFailed()) {
            log.error("validate asset info failed, {}", result);
            return Results.fail(result);
        }

        for (WalletAction action : actions) {
            BalanceSnapshot snapshot = walletIdToSnapshot.get(action.getWalletId());
            if (!WalletTxnHelper.actionSnapshotMatched(snapshot, action)) {
                log.error("validate action snapshot mismatch, action: {}, snapshot: {}", action, snapshot);
                return Results.fail(ErrorCode.BALANCE_SNAPSHOT_MISMATCH, "action snapshot mismatch");
            }

            if (!Strings.isEmpty(action.getReservationId())) {
                WalletReservation reservation = walletIdToReservation.get(action.getReservationId());
                if (!WalletTxnHelper.actionReservationMatched(action, reservation)) {
                    log.error("validate action reservation mismatch, action: {}, reservation: {}", action, reservation);
                    return Results.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, "action reservation mismatch");
                }
            }
        }
        return Results.success();
    }

//    {

    // get reservation from db to do precheck
//        Set<String> reservationRefs = linePbs.stream()
//                .filter((line)-> !Strings.isEmpty(line.getReservationRef()))
//                .distinct().map(TransactionLinePb::getReservationRef).collect(Collectors.toSet());
//        List<WalletReservation> reservationsInRequest = new ArrayList<>();
//        if (!reservationRefs.isEmpty()) {
//            reservationsInRequest = walletReservationManager.selectByRefs(new ArrayList<>(reservationRefs));
//        }
//        Map<String, WalletReservation> refToReservation = reservationsInRequest.stream().collect(Collectors.toMap(WalletReservation::getReferenceId, reservation->reservation));
//
//        // get snapshot from db to do precheck
//        Set<String> walletRefs = linePbs.stream().distinct().map(TransactionLinePb::getWalletRef).collect(Collectors.toSet());
//        List<BalanceSnapshot> balanceSnapshots = balanceSnapshotManager.selectByRefs(serviceId, new ArrayList<>(walletRefs));
//        Map<String, BalanceSnapshot> walletRefToSnapshot = balanceSnapshots.stream().collect(Collectors.toMap(BalanceSnapshot::getWalletReferenceId, snapshot -> snapshot));
//        List<RequestInfo.Line> lines = new ArrayList<>();
//        for (TransactionLinePb line : linePbs) {
//            BalanceSnapshot snapshot = walletRefToSnapshot.get(line.getWalletRef());
//            result = validateSnapshot(snapshot, line);
//            if (result.isFailed()) {
//                log.error("validate snapshot failed, {}", result);
//                return Result.fail(result);
//            }
//
//            String reservationId = null;
//            if (!Strings.isEmpty(line.getReservationRef())) {
//                WalletReservation reservation = refToReservation.get(line.getReservationRef());
//                result = validateReservation(reservation, snapshot, line);
//                if (result.isFailed()) {
//                    log.error("validate reservation failed, {}", result);
//                    return Result.fail(result);
//                }
//                reservationId = reservation.getReservationId();
//            }
//
//            lines.add(new RequestInfo.Line(snapshot.getWalletId(), line.getWalletRef(), line.getAssetCode(), line.getOperationType(), line.getAmount(), reservationId));
//        }
//
//        return Result.success(new RequestInfo(serviceId, businessType, transactionType, needPostLedger, balanceSnapshots, reservationsInRequest, lines));
//    }

    private Result<Void> validateAssetInfo(List<WalletAction> actions) {
        Map<String, Long> inAssetToAmount = new HashMap<>(), outAssetToAmount = new HashMap<>();
        for (WalletAction action : actions) {
            if (action.isTransfer()) {
                Map<String, Long> targetAssetToAmount;
                if (action.getActionType() == ActionType.TRANSFER_OUT) {
                    targetAssetToAmount = outAssetToAmount;
                } else {
                    targetAssetToAmount = inAssetToAmount;
                }
                targetAssetToAmount.putIfAbsent(action.getAssetId(), 0L);
                targetAssetToAmount.put(action.getAssetId(), action.getAmount() + targetAssetToAmount.get(action.getAssetId()));
            }
        }

        if (inAssetToAmount.size() != outAssetToAmount.size()) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "abnormal lines, asset types not match");
        }
        // validate: for each asset, transfer in should match transfer out
        for (Map.Entry<String, Long> entry : inAssetToAmount.entrySet()) {
            String assetCode = entry.getKey();
            Long inAmount = entry.getValue();
            if (!Objects.equals(inAmount, outAssetToAmount.get(assetCode))) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced asset: " + assetCode + ", in_amount: " + inAmount + ", out_amount" + outAssetToAmount.get(assetCode));
            }
        }
        return Results.success();
    }

//    // snapshot could be not open when transaction has already been completed
//    private Result<Void> validateSnapshot(BalanceSnapshot snapshot, WalletAction action) {
//        if (snapshot == null) {
//            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "snapshot not found, action: " + action);
//        }
//        if (!Objects.equals(snapshot.getWalletId(), action.getWalletId()) || !Objects.equals(snapshot.getAssetId(), action.getAssetId())) {
//            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_MISMATCH, String.format("snapshot mismatch, snapshot: %s, action: %s", snapshot, action));
//        }
//        return Result.success();
//    }
//
//    // reservation could be finished when transaction has already been completed
//    private Result<Void> validateReservation(WalletReservation reservation, BalanceSnapshot snapshot, WalletAction action) {
//        if (reservation == null) {
//            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation not found: " + action);
//        }
//        if (!Objects.equals(reservation.getWalletId(), snapshot.getWalletId()) || !Objects.equals(reservation.getAssetId(), snapshot.getAssetId())
//                || !Objects.equals(reservation.getAssetId(), action.getAssetId())) {
//            return Result.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, String.format("reservation mismatch, reservation: %s, snapshot: %s, action: %s", reservation, snapshot, action));
//        }
//        return Result.success();
//    }

    private Result<Void> updateDbToCreateTxn(WalletTransaction walletTxn, List<WalletAction> actions, List<BalanceSnapshot> balanceSnapshots,
                                              List<WalletReservation> reservationsInRequest, List<WalletReservation> createdReservations, Outbox outbox) {

        Map<String, List<WalletAction>> walletIdToActions = new HashMap<>();
        for (WalletAction action : actions) {
            walletIdToActions.computeIfAbsent(action.getWalletId(), k -> new ArrayList<>());
            walletIdToActions.get(action.getWalletId()).add(action);
        }

        // immediately claim the outbox and attempt to publish it right after update db
        return DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            Result<Void> result = updateReservations(reservationsInRequest,
                    (reservation) -> updateReservationBeforePosting(reservation, walletIdToActions));
            if (result.isFailed()) {
                log.error("update reservations failed, {}, {}", result, reservationsInRequest);
                return Results.fail(result);
            }


            result = updateSnapshotsBeforePosting(walletIdToActions, balanceSnapshots);
            if (result.isFailed()) {
                log.error("update snapshots failed, {}, {}", result, balanceSnapshots);
                return Results.fail(result);
            }

            if (!createdReservations.isEmpty()) {
                if (walletReservationManager.batchInsert(createdReservations) != createdReservations.size()) {
                    log.error("insert reservations failed, {}, {}", result, createdReservations);
                    return Results.fail(ErrorCode.WALLET_RESERVATION_DUPLICATED, "unexpected reservation duplicated");
                }
            }

            if (walletTransactionManager.insertIgnore(walletTxn) == 0) {
                log.error("insert transaction failed, {}, {}", result, walletTxn);
                return Results.fail(ErrorCode.WALLET_TRANSACTION_DUPLICATED, "unexpected transaction duplicated");
            }
            if (walletActionManager.batchInsert(actions) != actions.size()) {
                log.error("insert actions failed, {}, {}", result, actions);
                return Results.fail(ErrorCode.WALLET_ACTION_DUPLICATION, "unexpected action duplicated");
            }
            if (outbox != null) {
                // immediately claim the outbox and attempt to publish it right after update db
                if (outboxManager.insertWithClaim(outbox, LocalDateTimeHelper.nowAfterMs(OutboxConstant.BASE_ATTEMPT_INTERVAL_MS))
                        == 0) {
                    log.error("insert outbox failed, {}, {}", result, outbox);
                    return Results.fail(ErrorCode.WALLET_OUTBOX_DUPLICATED, "unexpected outbox duplicated");
                }
            }

            return Results.success();
        });
    }

    private Result<TransactionInfo> getTxnInfo(String txnId) {
        WalletTransaction txn = walletTransactionManager.selectByTxnId(txnId);
        if (txn == null) {
            log.error("getTxnInfo failed, {}", txnId);
            return Results.fail(ErrorCode.WALLET_TRANSACTION_NOT_FOUND, "no txn found for txnId: " + txnId);
        }

        List<WalletAction> afterPostingActions = walletActionManager.selectByTxnId(txn.getTxnId(), new LambdaQueryWrapper<>() {{
                in(WalletAction::getActionType, ActionType.TRANSFER_IN, ActionType.TRANSFER_OUT, ActionType.CONSUME);
            }});

        List<String> walletIds = afterPostingActions.stream().map(WalletAction::getWalletId).distinct().collect(Collectors.toList());
        List<BalanceSnapshot> snapshots = balanceSnapshotManager.selectByWalletIds(walletIds);
        if (snapshots.size() != walletIds.size()) {
            log.error("getTxnInfo failed, snapshot not found, txn: {}, walletIds: {}, snapshotsInDb: {}", txnId, walletIds, snapshots);
            return Results.fail(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "snapshot not found");
        }

        List<WalletAction> consumeActions = afterPostingActions.stream().filter(action -> action.getActionType() == ActionType.CONSUME).collect(Collectors.toList());
        List<WalletReservation> consumeReservations = new ArrayList<>();
        if (!consumeActions.isEmpty()) {
            List<String> consumeReservationRefs = consumeActions.stream().map(WalletAction::getReservationId).distinct().collect(Collectors.toList());
            consumeReservations = walletReservationManager.selectByRefs(consumeReservationRefs);
            if (consumeReservationRefs.size() != consumeReservations.size()) {
                log.error("reservation size mismatch, reservationFromDb: {}, reservationRefs: {}", consumeReservationRefs, consumeReservations);
                return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation size mismatch");
            }
        }

//        // get reservation created by the txn
//        List<WalletReservation> reservationsUnderTxn = walletReservationManager.selectByTxnId(txn.getTxnId());
//        List<WalletReservation> relatedReservations = new ArrayList<>();
//        List<String> reservationRefsInRequest = requestInfo.reservationsInTheRequest.stream().map(WalletReservation::getReferenceId).collect(Collectors.toList());
//        if (!reservationRefsInRequest.isEmpty()) {
//            // get reservation operated by the txn
//            relatedReservations = walletReservationManager.selectByRefs(reservationRefsInRequest);
//            if (relatedReservations.size() != reservationRefsInRequest.size()) {
//                log.error("reservation size mismatch, reservationFromDb: {}, reservationRefs: {}", relatedReservations, reservationRefsInRequest);
//                return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation size mismatch");
//            }
//        }
//        // all reservations
//        relatedReservations.addAll(reservationsUnderTxn);

        // only validate CONSUME/TRANSFER_IN/TRANSFER_OUT actions, only CONSUME have reservation
        Result<Void> result = validateActionInfo(afterPostingActions, snapshots, consumeReservations);
        if (result.isFailed()) {
            log.error("validate action after get txn failed, result: {}", result);
            return Results.fail(result);
        }

        TransactionInfo transactionInfo = TransactionInfo.create(txn, afterPostingActions, consumeReservations, snapshots);
        return Results.success(transactionInfo);
    }

//    private Result<Void> validateTransferInfo(TransactionInfo transactionInfo, RequestInfo requestInfo) {
//        if (requestInfo.transactionType != transactionInfo.walletTxn.getTxnType()) {
//            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "transaction type mismatch, transaction type: " + transactionInfo.walletTxn.getTxnType());
//        }
//        // TODO check if request info matches, maybe save request info in txn metadata
//
//        return Result.success();
//    }

    private Result<Outbox> createOutbox(WalletTransaction txn, List<WalletAction> actions) {
//        if (true) {throw new RuntimeException();}
        List<String> walletIds = actions.stream()
                .filter((action) -> action.getActionType() == ActionType.TRANSFER_IN || action.getActionType() == ActionType.TRANSFER_OUT)
                .map(WalletAction::getWalletId)
                .distinct()
                .collect(Collectors.toList());
        List<WalletAccountMapping> mappings = walletAccountMappingManager.selectInWalletIds(walletIds);
        if (mappings.size() != walletIds.size()) {
            log.error("wallet_account_mapping_not_found, wallet_ids: {}, mappings: {}", walletIds, mappings);
            return Results.fail(ErrorCode.WALLET_ACCOUNT_MAPPING_NOT_FOUND, "wallet_account_mapping_not_found");
        }
        Map<String, String> walletToAccount = mappings.stream().collect(Collectors.toMap(WalletAccountMapping::getWalletId, WalletAccountMapping::getAccountRefId));

        List<LedgerEntryPb> entries = new ArrayList<>();
        for (WalletAction action : actions) {
            LedgerEntryPb.Builder builder = LedgerEntryPb.newBuilder();
            builder.setAccountRef(walletToAccount.get(action.getWalletId())).setAmount(action.getAmount()).setAssetId(action.getAssetId());
            switch (action.getActionType()) {
                case TRANSFER_OUT:
                    builder.setDirection(LedgerDirectionPb.LedgerDirection_Debit);
                    break;
                case TRANSFER_IN:
                    builder.setDirection(LedgerDirectionPb.LedgerDirection_Credit);
                    break;
                default:
                    continue;
            }
            entries.add(builder.build());
        }

        PostTransactionRequestPb req = PostTransactionRequestPb.newBuilder()
                .setReferenceId(txn.getTxnId())
                .addAllEntries(entries).build();
        Outbox outbox = OutboxHelper.fromPostTransactionRequest(req, txn);
        return Results.success(outbox);
    }

    private Result<WalletReservation> updateReservationBeforePosting(WalletReservation reservation, Map<String, List<WalletAction>> walletIdToActions) {
        for (WalletAction action : walletIdToActions.get(reservation.getWalletId())) {
            switch (action.getActionType()) {
                case CONSUME:
                    if (reservation.getRemaining() < action.getAmount()) {
                        return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_SATISFIED, String.format("fail to consume, remaining not enough, reservation: %s, action: %s ", reservation, action));
                    }
                    reservation.setRemaining(reservation.getRemaining() - action.getAmount());
                    reservation.setPendingSettle(reservation.getPendingSettle() + action.getAmount());
                    break;
                case RELEASE:
                    if (reservation.getRemaining() < action.getAmount()) {
                        return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_SATISFIED, String.format("fail to release, remaining not enough, reservation: %s, action: %s ", reservation, action));
                    }
                    reservation.setRemaining(reservation.getRemaining() - action.getAmount());
                    reservation.setReleased(reservation.getReleased() + action.getAmount());
                    if (WalletReservation.isTotallySettled(reservation)) {
                        reservation.setReservationStatus(ReservationStatus.FINISHED);
                        reservation.setReservationOutcome(WalletReservation.inferOutcome(reservation));
                    }
                    break;
            }
        }
        return Results.success(reservation);
    }

    private Result<WalletReservation> updateReservationAfterPosting(WalletReservation reservation, Map<String, List<WalletAction>> walletIdToActions) {
        for (WalletAction action : walletIdToActions.get(reservation.getWalletId())) {
            if (Objects.requireNonNull(action.getActionType()) == ActionType.CONSUME) {
                if (reservation.getPendingSettle() < action.getAmount()) {
                    // TODO maybe throw exception? this is severe error (overspend risk in reservation)
                    return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_SATISFIED, String.format("fail to consume, pending settle not enough, reservation: %s, action: %s ", reservation, action));
                }
                reservation.setPendingSettle(reservation.getPendingSettle() - action.getAmount());
                reservation.setConsumed(reservation.getConsumed() + action.getAmount());
                if (WalletReservation.isTotallySettled(reservation)) {
                    reservation.setReservationStatus(ReservationStatus.FINISHED);
                    reservation.setReservationOutcome(WalletReservation.inferOutcome(reservation));
                }
            }
        }
        return Results.success(reservation);
    }

    private Result<Void> updateReservations(List<WalletReservation> reservations, Function<WalletReservation, Result<WalletReservation>> function) {
        if (reservations.isEmpty()) {
            return Results.success();
        }
        List<Long> modifyReservationIds = reservations.stream().map(WalletReservation::getId).collect(Collectors.toList());
        // select for update to lock the rows in PK order
        var reservationFromDb = walletReservationManager.selectInIdForUpdate(modifyReservationIds);
        if (reservationFromDb.size() != modifyReservationIds.size()) {
            return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, String.format("wallet_reservation_not_found, ids: %s, reservationFromDb: %s", modifyReservationIds, reservationFromDb));
        }

        for (WalletReservation reservation : reservationFromDb) {
            WalletReservation copy = new WalletReservation();
            BeanUtils.copyProperties(reservation, copy);
            Result<WalletReservation> result = function.apply(reservation);
            if (result.isFailed()) {
                return Results.fail(result);
            }
            reservation = result.value;

            if (!Objects.equals(reservation, copy)) {
                if (walletReservationManager.updateWithOptimisticLock(reservation, copy) != 1) {
                    return Results.fail(ErrorCode.WALLET_RESERVATION_UPDATE_FAILED, String.format("failed to update reservation, reservation: %s, copy: %s", reservation, copy));
                }
            }
        }
        return Results.success();
    }

    private Result<Void> updateSnapshotsBeforePosting(Map<String, List<WalletAction>> walletIdToActions, List<BalanceSnapshot> snapshots) {
        List<BalanceSnapshot> snapshotInOrder = snapshots.stream().sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());
        // update snapshot to reserve/release money by id order
        // snapshotInOrder is memory cache, not real time data, only use wallet_id and id here
        for (BalanceSnapshot snapshot : snapshotInOrder) {
            // no need to combine update for one snapshot, basically there should only be one RESERVE or RELEASE for one snapshot for now
            // TODO maybe combine update in the future
            for (WalletAction action : walletIdToActions.get(snapshot.getWalletId())) {
                switch (action.getActionType()) {
                    case RESERVE:
                        if (balanceSnapshotManager.reserveFromWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount())
                                != 1) {
                            log.error("failed to update snapshot to reserve amount, reservation: {}, action: {}", snapshot, action);
                            return Results.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to reserve amount");
                        }
                        break;
                    case RELEASE:
                        if (balanceSnapshotManager.releaseFromWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount())
                                != 1) {
                            log.error("failed to update snapshot to release amount, reservation: {}, action: {}", snapshot, action);
                            return Results.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to release amount");
                        }
                }
            }
        }
        return Results.success();
    }

    private Result<Void> updateSnapshotsAfterPosting(Map<String, List<WalletAction>> walletIdToActions, List<BalanceSnapshot> snapshots) {
        List<BalanceSnapshot> snapshotInOrder = snapshots.stream().sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());
        for (BalanceSnapshot snapshot : snapshotInOrder) {
            // TODO maybe combine update in the future
            for (WalletAction action : walletIdToActions.get(snapshot.getWalletId())) {
                switch (action.getActionType()) {
                    case TRANSFER_OUT:
                        if (balanceSnapshotManager.transferOutFromWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount())
                                != 1) {
                            log.error("failed to update snapshot to transfer out amount, reservation: {}, action: {}", snapshot, action);
                            return Results.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to transfer out amount");
                        }
                        break;
                    case TRANSFER_IN:
                        if (balanceSnapshotManager.transferInToWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount())
                                != 1) {
                            log.error("failed to update snapshot to transfer in amount, reservation: {}, action: {}", snapshot, action);
                            return Results.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to transfer in amount");
                        }
                        break;
                }
            }
        }
        return Results.success();
    }

    // TODO remove this
//    Result<Void> postLedger(TransactionInfo transactionInfo) {
    ////        if (true) {throw new RuntimeException();}
//        List<String> walletIds = new ArrayList<>(transactionInfo.walletIdToPostActions.keySet());
//        List<WalletAccountMapping> mappings = walletAccountMappingManager.selectInWalletIds(walletIds);
//        if (mappings.size() != walletIds.size()) {
//            log.error("wallet_account_mapping_not_found, wallet_ids: {}, mappings: {}", walletIds, mappings);
//            return Result.fail(ErrorCode.WALLET_ACCOUNT_MAPPING_NOT_FOUND, "wallet_account_mapping_not_found");
//        }
//        Map<String, String> walletToAccount = mappings.stream().collect(Collectors.toMap(WalletAccountMapping::getWalletId, WalletAccountMapping::getAccountRefId));
//
//        List<LedgerEntryPb> entries = new ArrayList<>();
//        for (List<WalletAction> actions : transactionInfo.walletIdToPostActions.values()) {
//            for (WalletAction action : actions) {
//                LedgerEntryPb.Builder builder = LedgerEntryPb.newBuilder();
//                builder.setAccountRef(walletToAccount.get(action.getWalletId())).setAmount(action.getAmount()).setAssetId(action.getAssetId());
//                switch (action.getActionType()) {
//                    case TRANSFER_OUT:
//                        builder.setDirection(LedgerDirectionPb.LedgerDirection_Debit);
//                        break;
//                    case TRANSFER_IN:
//                        builder.setDirection(LedgerDirectionPb.LedgerDirection_Credit);
//                        break;
//                    default:
//                        continue;
//                }
//                entries.add(builder.build());
//            }
//        }
//
//        PostTransactionRequestPb req = PostTransactionRequestPb.newBuilder()
//                .setReferenceId(transactionInfo.walletTxn.getTxnId())
//                .addAllEntries(entries).build();
//        Result<PostTransactionReplyPb> result = postServiceClient.postTransaction(req);
//        if (result.isFailed()) {
//            log.error("failed to post transaction, req: {}, result: {}", req, result);
//            return Result.fail(result);
//        }
//        return Result.success();
//    }

    public Result<WalletTransaction> afterPostLedger(String txnId) {
        Result<TransactionInfo> txnInfoResult = getTxnInfo(txnId);
        if (txnInfoResult.isFailed()) {
            log.error("failed to find transaction info, txnId: {}, result: {}", txnId, txnInfoResult);
            return Results.fail(txnInfoResult);
        }
        TransactionInfo txnInfo = txnInfoResult.value;
        if (txnInfo.walletTxn.hasFinalized()) {
            log.info("wallet txn has finalized, txnId: {}", txnId);
            return Results.success(txnInfo.walletTxn);
        }

        List<BalanceSnapshot> snapshotIdsInOrder = txnInfo.snapshots.stream()
                .sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());

        Result<Void> outResult = DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            // should update all reservations here
            Result<Void> result = updateReservations(txnInfo.reservations,
                    (reservation) -> updateReservationAfterPosting(reservation, txnInfo.walletIdToPostActions));
            if (result.isFailed()) {
                return Results.fail(result);
            }

            result = updateSnapshotsAfterPosting(txnInfo.walletIdToPostActions, snapshotIdsInOrder);
            if (result.isFailed()) {
                return Results.fail(result);
            }
            if (walletTransactionManager.updateTransactionStatus(txnInfo.walletTxn.getId(), TransactionStatus.PENDING, TransactionStatus.COMPLETED)
                    != 1) {
                log.error("wallet_transaction_update_failed, transaction: {}", txnInfo.walletTxn);
                return Results.fail(ErrorCode.WALLET_TRANSACTION_UPDATE_FAILED, "transaction_update_failed");
            }
            return Results.success();
        });
        if (outResult.isFailed()) {
            return Results.fail(outResult);
        }
        // outbox should not block main flow
//        if (walletOutboxManager.finishOutbox(transactionInfo.outbox) != 1) {
//            log.error("finish outbox failed, outbox={}", transactionInfo.outbox);
//        }
        WalletTransaction txn = walletTransactionManager.selectById(txnInfo.walletTxn.getId());
        if (txn == null) {
            log.error("wallet_transaction_not_found, transaction: {}", txnInfo.walletTxn);
            return Results.fail(ErrorCode.WALLET_TRANSACTION_NOT_FOUND, "wallet_transaction_not_found");
        }
        return Results.success(txn);
    }



    WalletTransaction createTransaction(TransactionType transactionType, String referenceId, ServiceId serviceId, String idempotencyKey, BusinessType businessType) {
        return WalletTransaction.create(
                IdGenerator.generateWalletTransactionId(),
                referenceId,
                serviceId,
                idempotencyKey,
                TransactionStatus.PENDING,
                transactionType,
                businessType
        );
    }

    WalletReservation createReservation(WalletTransaction walletTransaction, String walletId, TransactionLinePb line) {
        switch (line.getOperationType()) {
            case OperationTypePb_Debit:
                // in Atomic txn, reservation asset goes into pending settle after created
                return  WalletReservation.create(
                        IdGenerator.generateReservationId(),
                        walletTransaction.getInitiator(),
                        walletTransaction.getReferenceId() + ":" + walletId,
                        walletId,
                        line.getWalletRef(),
                        line.getAssetCode(),
                        line.getAmount(),
                        0L,
                        0L,
                        line.getAmount(),
                        0L,
                        ReservationStatus.ACTIVE,
                        ReservationOutcome.NOT_DONE,
                        walletTransaction.getTxnId()
                );
            case OperationTypePb_Reserve:
                return  WalletReservation.create(
                        IdGenerator.generateReservationId(),
                        walletTransaction.getInitiator(),
                        walletTransaction.getReferenceId() + ":" + walletId,
                        walletId,
                        line.getWalletRef(),
                        line.getAssetCode(),
                        line.getAmount(),
                        line.getAmount(),
                        0L,
                        0L,
                        0L,
                        ReservationStatus.ACTIVE,
                        ReservationOutcome.NOT_DONE,
                        walletTransaction.getTxnId()
                );
            default: throw new InvalidEnumException("OperationType_Reserve not supported: " + line.getOperationType());
        }
    }

//    WalletAction createReserveAction(WalletTransaction walletTxn, RequestInfo.Line line) {
//        return createAction(walletTxn.getTxnId(), line, WalletBucket.AVAILABLE, ActionType.RESERVE);
//    }
//
//    WalletAction createConsumeAction(WalletTransaction walletTxn, RequestInfo.Line line, String reservationId) {
//        return createAction(walletTxn.getTxnId(), line, WalletBucket.RESERVED, ActionType.CONSUME);
//    }
//
//    private WalletAction createTransferAction(WalletTransaction walletTxn, RequestInfo.Line line) {
//        WalletBucket bucket;
//        ActionType actionType;
//        switch (line.operationTypePb) {
//            case OperationType_Debit:
//                bucket = WalletBucket.RESERVED;
//                actionType = ActionType.TRANSFER_OUT;
//                break;
//            case OperationType_Credit:
//                bucket = WalletBucket.AVAILABLE;
//                actionType = ActionType.TRANSFER_IN;
//                break;
//            default:
//                throw new InvalidEnumException("invalid operation type: " + line.operationTypePb);
//        }
//        return createAction(walletTxn.getTxnId(), line, bucket, actionType);
//    }

    private List<WalletAction> createActionsFromLine(String txnId, String walletId, String reservationId, TransactionLinePb line) {
        List<WalletAction> actions = new ArrayList<>();
        switch (line.getOperationType()) {
            case OperationTypePb_Reserve:
                actions.add(createAction(txnId, walletId, null, WalletBucket.AVAILABLE, ActionType.RESERVE, line));
                break;
            case OperationTypePb_Earmark:
                actions.add(createAction(txnId, walletId, reservationId, WalletBucket.RESERVED, ActionType.CONSUME, line));
                actions.add(createAction(txnId, walletId, null, WalletBucket.RESERVED, ActionType.TRANSFER_OUT, line));
                break;
            case OperationTypePb_Release:
                actions.add(createAction(txnId, walletId, reservationId, WalletBucket.RESERVED, ActionType.RELEASE, line));
                break;
            case OperationTypePb_Debit:
                actions.add(createAction(txnId, walletId, null, WalletBucket.AVAILABLE, ActionType.RESERVE, line));
                actions.add(createAction(txnId, walletId, reservationId, WalletBucket.RESERVED, ActionType.CONSUME, line));
                actions.add(createAction(txnId, walletId, null, WalletBucket.RESERVED, ActionType.TRANSFER_OUT, line));
                break;
            case OperationTypePb_Credit:
                actions.add(createAction(txnId, walletId, null, WalletBucket.AVAILABLE, ActionType.TRANSFER_IN, line));
                break;
            default:
                throw new InvalidEnumException("invalid operation type: " + line.getOperationType());
        }
        return actions;
    }

    private WalletAction createAction(String txnId, String walletId, String reservationId, WalletBucket bucket, ActionType actionType, TransactionLinePb line) {
        return WalletAction.create(
                IdGenerator.generateWalletActionId(),
                txnId,
                walletId,
                line.getAssetCode(),
                bucket,
                actionType,
                line.getAmount(),
                reservationId
        );
    }

//    WalletOutbox createOutbox(WalletTransaction walletTransaction, RequestInfo requestInfo) {
//        return WalletOutbox.create(OutboxEventType.LEDGER_POST,
//                walletTransaction.getReferenceId(),
//                walletTransaction.getReferenceId(),
//                OutboxStatus.PENDING,
//                //TODO payload
//                "{\"TODO\":\"todo\"}",
//                0,
//                OutboxHelper.nextAttempt()
//        );
//    }

//    // memory cache, not real time data
//    @AllArgsConstructor
//    static class RequestInfo {
//        final ServiceId serviceId;
//        final BusinessType businessType;
//        final TransactionType transactionType;
//        final boolean needPostLedger;
//        final List<BalanceSnapshot> snapshots;
//        //        final Map<OperationTypePb, List<BalanceSnapshot>> operationToSnapshots;
//        // only have reservations in the request (created by other txn), not including txn created by this txn
//        final List<WalletReservation> reservationsInTheRequest;
//        final List<Line> lines;
//        @AllArgsConstructor
//        static class Line {
//            String walletId;
//            String walletRef;
//            String assetCode;
//            OperationTypePb operationTypePb;
//            Long amount;
//            String reservationId;
//        }
//    }

    // memory cache, not real time data
    @AllArgsConstructor
    static class TransactionInfo {
        final WalletTransaction walletTxn;
        final List<WalletAction> actions;
        final Map<String, List<WalletAction>> walletIdToPostActions; // only CONSUME, TRANSFER_OUT, TRANSFER_IN
        final List<WalletReservation> reservations;
        final List<BalanceSnapshot> snapshots;

        static TransactionInfo create(WalletTransaction txn, List<WalletAction> actions, List<WalletReservation> reservations, List<BalanceSnapshot> snapshots) {
            Map<String, List<WalletAction>> walletIdToActions = new HashMap<>();
            for (WalletAction action : actions) {
                if (!action.isTransfer()) {
                    continue;
                }
                walletIdToActions.computeIfAbsent(action.getWalletId(), k -> new ArrayList<>());
                walletIdToActions.get(action.getWalletId()).add(action);
            }
            return new TransactionInfo(txn, actions, walletIdToActions, reservations, snapshots);
        }

        static TransactionInfo create(WalletTransaction walletTxn) {
            return new TransactionInfo(walletTxn, null, null, null, null);
        }
    }
}