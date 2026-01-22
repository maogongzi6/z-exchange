package com.exchange.app.wallet.processor.transaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.wallet.client.PostServiceClient;
import com.exchange.app.wallet.cronjob.outbox.constant.OutboxConfig;
import com.exchange.app.wallet.dao.manager.*;
import com.exchange.app.wallet.exception.InvalidEnumException;
import com.exchange.app.wallet.kafka.producer.PostLedgerPublisher;
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
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.DbTransactionHelper;
import com.exchange.app.wallet.utils.EnumPbMappers;
import com.exchange.app.wallet.utils.IdGenerator;
import com.exchange.app.wallet.utils.OutboxHelper;
import com.exchange.common.outbox.dao.manager.OutboxManager;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.time.LocalDateTimeHelper;
import com.exchange.proto.common.event.EventEnvelope;
import com.exchange.proto.common.event.EventEnvelopePb;
import com.exchange.proto.ledger.common.LedgerDirectionPb;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
// package private
class TransactionProcessor {
    private final TransactionTemplate transactionTemplate;

    private final PostServiceClient postServiceClient;

    private final BalanceSnapshotManager balanceSnapshotManager;
    private final WalletReservationManager walletReservationManager;
    private final WalletTransactionManager walletTransactionManager;
    private final WalletActionManager walletActionManager;
    private final WalletAccountMappingManager walletAccountMappingManager;
    private final OutboxManager outboxManager;
    private final PostLedgerPublisher postLedgerPublisher;

    Result<RequestInfo> validateAndGetRequestInfo(ServiceIdPb serviceIdPb, BusinessTypePb businessTypePb, TransactionType transactionType, boolean needPostLedger, List<TransactionLinePb> linePbs) {
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(serviceIdPb);
        BusinessType businessType = EnumPbMappers.businessTypePbMapper.to(businessTypePb);
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid service id: " + serviceIdPb);
        }
        if (businessType == null || businessType == BusinessType.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid business type: " + businessTypePb);
        }
        Result<Void> result = validateAssetInfo(linePbs);
        if (!result.success) {
            log.error("validate asset info failed, {}", result);
            return Result.fail(result);
        }

        // get reservation from db to do precheck
        Set<String> reservationRefs = linePbs.stream()
                .filter((line)-> !Strings.isEmpty(line.getReservationRef()))
                .distinct().map(TransactionLinePb::getReservationRef).collect(Collectors.toSet());
        List<WalletReservation> reservationsInRequest = new ArrayList<>();
        if (!reservationRefs.isEmpty()) {
            reservationsInRequest = walletReservationManager.selectByRefs(new ArrayList<>(reservationRefs));
        }
        Map<String, WalletReservation> refToReservation = reservationsInRequest.stream().collect(Collectors.toMap(WalletReservation::getReferenceId, reservation->reservation));

        // get snapshot from db to do precheck
        Set<String> walletRefs = linePbs.stream().distinct().map(TransactionLinePb::getWalletRef).collect(Collectors.toSet());
        List<BalanceSnapshot> balanceSnapshots = balanceSnapshotManager.selectByRefs(serviceId, new ArrayList<>(walletRefs));
        Map<String, BalanceSnapshot> walletRefToSnapshot = balanceSnapshots.stream().collect(Collectors.toMap(BalanceSnapshot::getWalletReferenceId, snapshot -> snapshot));
        List<RequestInfo.Line> lines = new ArrayList<>();
        for (TransactionLinePb line : linePbs) {
            BalanceSnapshot snapshot = walletRefToSnapshot.get(line.getWalletRef());
            result = validateSnapshot(snapshot, line);
            if (!result.success) {
                log.error("validate snapshot failed, {}", result);
                return Result.fail(result);
            }

            String reservationId = null;
            if (!Strings.isEmpty(line.getReservationRef())) {
                WalletReservation reservation = refToReservation.get(line.getReservationRef());
                result = validateReservation(reservation, snapshot, line);
                if (!result.success) {
                    log.error("validate reservation failed, {}", result);
                    return Result.fail(result);
                }
                reservationId = reservation.getReservationId();
            }

            lines.add(new RequestInfo.Line(snapshot.getWalletId(), line.getWalletRef(), line.getAssetCode(), line.getOperationType(), line.getAmount(), reservationId));
        }

        return Result.success(new RequestInfo(serviceId, businessType, transactionType, needPostLedger, balanceSnapshots, reservationsInRequest, lines));
    }

    private Result<Void> validateAssetInfo(List<TransactionLinePb> linePbs) {
        Map<String, Long> inAssetToAmount = new HashMap<>(), outAssetToAmount = new HashMap<>();
        for (TransactionLinePb line : linePbs) {
            // validate action type and amount
            if (line.getOperationType() == OperationTypePb.UNRECOGNIZED || line.getOperationType() == OperationTypePb.OperationTypePb_Unknown) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid operation type: " + line);
            }
            if (line.getAmount() <= 0) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid amount: " + line);
            }

            if (isTransfer(line.getOperationType())) {
                Map<String, Long> targetAssetToAmount;
                if (isTransferOut(line.getOperationType())) {
                    // debit and earmark are asset transferring out
                    targetAssetToAmount = outAssetToAmount;
                } else {
                    // credit is asset transferring in
                    targetAssetToAmount = inAssetToAmount;
                }
                targetAssetToAmount.putIfAbsent(line.getAssetCode(), 0L);
                targetAssetToAmount.put(line.getAssetCode(), line.getAmount() + targetAssetToAmount.get(line.getAssetCode()));
            }
        }

        if (inAssetToAmount.size() != outAssetToAmount.size()) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "abnormal lines, asset types not match");
        }
        // validate: for each asset, transfer in should match transfer out
        for (Map.Entry<String, Long> entry : inAssetToAmount.entrySet()) {
            String assetCode = entry.getKey();
            Long inAmount = entry.getValue();
            if (!Objects.equals(inAmount, outAssetToAmount.get(assetCode))) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced asset: " + assetCode + ", in_amount: " + inAmount + ", out_amount" + outAssetToAmount.get(assetCode));
            }
        }
        return Result.success();
    }

    private boolean isTransfer(OperationTypePb operationTypePb) {
        return isTransferIn(operationTypePb) || isTransferOut(operationTypePb);
    }

    private boolean isTransferOut(OperationTypePb operationTypePb) {
        return operationTypePb == OperationTypePb.OperationTypePb_Earmark || operationTypePb == OperationTypePb.OperationTypePb_Debit;
    }

    private boolean isTransferIn(OperationTypePb operationTypePb) {
        return operationTypePb == OperationTypePb.OperationTypePb_Credit;
    }

    // snapshot could be not open when transaction has already been completed
    private Result<Void> validateSnapshot(BalanceSnapshot snapshot, TransactionLinePb line) {
        if (snapshot == null) {
            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "snapshot not found, line: " + line);
        }
        if (!Objects.equals(snapshot.getWalletReferenceId(), line.getWalletRef()) || !Objects.equals(snapshot.getAssetId(), line.getAssetCode())) {
            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_MISMATCH, String.format("snapshot mismatch, snapshot: %s, line: %s", snapshot, line));
        }
        return Result.success();
    }

    // reservation could be finished when transaction has already been completed
    private Result<Void> validateReservation(WalletReservation reservation, BalanceSnapshot snapshot, TransactionLinePb line) {
        if (reservation == null) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation not found: " + line);
        }
        if (!Objects.equals(reservation.getWalletId(), snapshot.getWalletId()) || !Objects.equals(reservation.getAssetId(), line.getAssetCode())) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, String.format("reservation mismatch, reservation: %s, snapshot: %s, line: %s", reservation, snapshot, line));
        }
        return Result.success();
    }

    Result<TransactionInfo> getTxnInfo(WalletTransaction txn, RequestInfo requestInfo) {
        List<WalletAction> afterPostingActions = new ArrayList<>();
//        WalletOutbox outbox = null;
        if (requestInfo.needPostLedger) {
            afterPostingActions = walletActionManager.selectByTxnId(txn.getTxnId(), new LambdaQueryWrapper<>() {{
                in(WalletAction::getActionType, ActionType.TRANSFER_IN, ActionType.TRANSFER_OUT, ActionType.CONSUME);
            }});
//            outbox = walletOutboxManager.selectByIdempotencyKey(txn.getReferenceId());
        }

        // get reservation created by the txn
        List<WalletReservation> reservationsUnderTxn = walletReservationManager.selectByTxnId(txn.getTxnId());
        List<WalletReservation> relatedReservations = new ArrayList<>();
        List<String> reservationRefsInRequest = requestInfo.reservationsInTheRequest.stream().map(WalletReservation::getReferenceId).collect(Collectors.toList());
        if (!reservationRefsInRequest.isEmpty()) {
            // get reservation operated by the txn
            relatedReservations = walletReservationManager.selectByRefs(reservationRefsInRequest);
            if (relatedReservations.size() != reservationRefsInRequest.size()) {
                log.error("reservation size mismatch, reservationFromDb: {}, reservationRefs: {}", relatedReservations, reservationRefsInRequest);
                return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation size mismatch");
            }
        }
        // all reservations
        relatedReservations.addAll(reservationsUnderTxn);

        // TODO fill outbox
        TransactionInfo transactionInfo = TransactionInfo.create(txn, afterPostingActions, relatedReservations, null);
        return Result.result(transactionInfo, validateTransferInfo(transactionInfo, requestInfo));
    }

    private Result<Void> validateTransferInfo(TransactionInfo transactionInfo, RequestInfo requestInfo) {
        if (requestInfo.transactionType != transactionInfo.walletTxn.getTxnType()) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "transaction type mismatch, transaction type: " + transactionInfo.walletTxn.getTxnType());
        }
        // TODO check if request info matches, maybe save request info in txn metadata

        return Result.success();
    }

    // reserve -> snapshot: -available, +reserved
    // consume -> reservation: -remaining, +pending_settle
    // release -> snapshot, reservation: -reserved, +available | -pending_settle, +released, change status
    Result<TransactionInfo> beforePostingLedger(String referenceId, String idempotencyKey, RequestInfo requestInfo) {
        WalletTransaction walletTxn = createTransaction(requestInfo.transactionType, referenceId, idempotencyKey, requestInfo);
        // complete txn immediately if no need to post ledger
        if (!requestInfo.needPostLedger) {
            walletTxn.setTxnStatus(TransactionStatus.COMPLETED);
        }

        // create action and reservation
        List<WalletAction> actions = new ArrayList<>();
        List<WalletReservation> createdReservations = new ArrayList<>();
        for (RequestInfo.Line line : requestInfo.lines) {
            List<WalletAction> actionsFromLine = createActionsFromLine(walletTxn.getTxnId(), line);
            switch (line.operationTypePb) {
                case OperationTypePb_Reserve:
                case OperationTypePb_Debit:
                    WalletReservation reservation = createReservation(walletTxn, line);
                    createdReservations.add(reservation);

                    // after creating reservation, fill reservation id into action
                    for (WalletAction action : actionsFromLine) {
                        if (action.getActionType() == ActionType.CONSUME || action.getActionType() == ActionType.RESERVE) {
                            action.setReservationId(reservation.getReservationId());
                        }
                    }
                    break;
            }
            actions.addAll(actionsFromLine);
        }


        Outbox outbox;
        if (requestInfo.needPostLedger) {
            Result<Outbox> result = createOutbox(walletTxn, actions);
            if (!result.success) {
                return Result.fail(result);
            }
            outbox = result.value;
        } else {
            outbox = null;
        }

        Map<String, List<WalletAction>> walletIdToActions = new HashMap<>();
        for (WalletAction action : actions) {
            walletIdToActions.computeIfAbsent(action.getWalletId(), k -> new ArrayList<>());
            walletIdToActions.get(action.getWalletId()).add(action);
        }

        Result<Void> outResult = DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            Result<Void> result = updateReservations(requestInfo.reservationsInTheRequest,
                    (reservation) -> updateReservationBeforePosting(reservation, walletIdToActions));
            if (!result.success) {
                log.error("update reservations failed, {}, {}", result, requestInfo.reservationsInTheRequest);
                return Result.fail(result);
            }


            result = updateSnapshotsBeforePosting(walletIdToActions, requestInfo.snapshots);
            if (!result.success) {
                log.error("update snapshots failed, {}, {}", result, requestInfo.snapshots);
                return Result.fail(result);
            }

            if (!createdReservations.isEmpty()) {
                if (walletReservationManager.batchInsert(createdReservations) != createdReservations.size()) {
                    log.error("insert reservations failed, {}, {}", result, createdReservations);
                    return Result.fail(ErrorCode.WALLET_RESERVATION_DUPLICATED, "unexpected reservation duplicated");
                }
            }

            if (walletTransactionManager.insertIgnore(walletTxn) == 0) {
                log.error("insert transaction failed, {}, {}", result, walletTxn);
                return Result.fail(ErrorCode.WALLET_TRANSACTION_DUPLICATED, "unexpected transaction duplicated");
            }
            if (walletActionManager.batchInsert(actions) != actions.size()) {
                log.error("insert actions failed, {}, {}", result, actions);
                return Result.fail(ErrorCode.WALLET_ACTION_DUPLICATION, "unexpected action duplicated");
            }
            if (requestInfo.needPostLedger) {
                // immediately claim the outbox and attempt to publish it right after update db
                if (outboxManager.insertWithClaim(outbox, LocalDateTimeHelper.nowAfterMs(OutboxConfig.BASE_ATTEMPT_INTERVAL_MS))
                        == 0) {
                    log.error("insert outbox failed, {}, {}", result, outbox);
                    return Result.fail(ErrorCode.WALLET_OUTBOX_DUPLICATED, "unexpected outbox duplicated");
                }
            }

            return Result.success();
        });
        if (!outResult.success) {
            return Result.fail(outResult);
        }

        if (requestInfo.needPostLedger) {
            Result<Void> result = postLedgerPublisher.publish(outbox);
            if (result.success) {
                if (outboxManager.updateStatusToFinalize(outbox, OutboxStatus.SENT, outbox.getLastAttemptAt())
                        == 1) {
                    outbox.setOutboxStatus(OutboxStatus.SENT);
                    outbox.setFinalizedAt(outbox.getLastAttemptAt());
                } else {
                    // do not block main flow
                    log.error("finalize outbox failed, {}, {}", outbox, result);
                }
            } else {
                // publish failed should not block main flow, and should return txn execute successfully
                log.error("post ledger publish failed, outbox: {}, result: {}", outbox, result);
            }
        }


        // TODO this part can be removed, reservation are queried from db after receiving reply from ledger
        // fill PK into created reservations
//        var reservations = walletReservationManager.selectByTxnId(walletTxn.getTxnId());
//        if (reservations.size() != createdReservations.size()) {
//            log.error("wallet_reservation_not_found, reservationsFromDb: {}, createdReservations: {}", reservations, createdReservations);
//            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "wallet_reservation_not_found");
//        }
        // all reservations related to the txn
        List<WalletReservation> reservations = new ArrayList<>(createdReservations);
        reservations.addAll(requestInfo.reservationsInTheRequest);

        return Result.success(TransactionInfo.create(walletTxn, actions, reservations, outbox));
    }

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
            return Result.fail(ErrorCode.WALLET_ACCOUNT_MAPPING_NOT_FOUND, "wallet_account_mapping_not_found");
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
                .setReferenceId(txn.getReferenceId())
                .addAllEntries(entries).build();
        Outbox outbox = OutboxHelper.fromPostTransactionRequest(req, txn);
        return Result.success(outbox);
    }

    private Result<WalletReservation> updateReservationBeforePosting(WalletReservation reservation, Map<String, List<WalletAction>> walletIdToActions) {
        for (WalletAction action : walletIdToActions.get(reservation.getWalletId())) {
            switch (action.getActionType()) {
                case CONSUME:
                    if (reservation.getRemaining() < action.getAmount()) {
                        return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_SATISFIED, String.format("fail to consume, remaining not enough, reservation: %s, action: %s ", reservation, action));
                    }
                    reservation.setRemaining(reservation.getRemaining() - action.getAmount());
                    reservation.setPendingSettle(reservation.getPendingSettle() + action.getAmount());
                    break;
                case RELEASE:
                    if (reservation.getRemaining() < action.getAmount()) {
                        return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_SATISFIED, String.format("fail to release, remaining not enough, reservation: %s, action: %s ", reservation, action));
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
        return Result.success(reservation);
    }

    private Result<WalletReservation> updateReservationAfterPosting(WalletReservation reservation, Map<String, List<WalletAction>> walletIdToActions) {
        for (WalletAction action : walletIdToActions.get(reservation.getWalletId())) {
            if (Objects.requireNonNull(action.getActionType()) == ActionType.CONSUME) {
                if (reservation.getPendingSettle() < action.getAmount()) {
                    // TODO maybe throw exception? this is severe error (overspend risk in reservation)
                    return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_SATISFIED, String.format("fail to consume, pending settle not enough, reservation: %s, action: %s ", reservation, action));
                }
                reservation.setPendingSettle(reservation.getPendingSettle() - action.getAmount());
                reservation.setConsumed(reservation.getConsumed() + action.getAmount());
                if (WalletReservation.isTotallySettled(reservation)) {
                    reservation.setReservationStatus(ReservationStatus.FINISHED);
                    reservation.setReservationOutcome(WalletReservation.inferOutcome(reservation));
                }
            }
        }
        return Result.success(reservation);
    }
    private Result<Void> updateReservations(List<WalletReservation> reservations, Function<WalletReservation, Result<WalletReservation>> function) {
        if (reservations.isEmpty()) {
            return Result.success();
        }
        List<Long> modifyReservationIds = reservations.stream().map(WalletReservation::getId).collect(Collectors.toList());
        // select for update to lock the rows in PK order
        var reservationFromDb = walletReservationManager.selectInIdForUpdate(modifyReservationIds);
        if (reservationFromDb.size() != modifyReservationIds.size()) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, String.format("wallet_reservation_not_found, ids: %s, reservationFromDb: %s", modifyReservationIds, reservationFromDb));
        }

        for (WalletReservation reservation : reservationFromDb) {
            WalletReservation copy = new WalletReservation();
            BeanUtils.copyProperties(reservation, copy);
            Result<WalletReservation> result = function.apply(reservation);
            if (!result.success) {
                return Result.fail(result);
            }
            reservation = result.value;

            if (!Objects.equals(reservation, copy)) {
                if (walletReservationManager.updateWithOptimisticLock(reservation, copy) != 1) {
                    return Result.fail(ErrorCode.WALLET_RESERVATION_UPDATE_FAILED, String.format("failed to update reservation, reservation: %s, copy: %s", reservation, copy));
                }
            }
        }
        return Result.success();
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
                            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to reserve amount");
                        }
                        break;
                    case RELEASE:
                        if (balanceSnapshotManager.releaseFromWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount())
                                != 1) {
                            log.error("failed to update snapshot to release amount, reservation: {}, action: {}", snapshot, action);
                            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to release amount");
                        }
                }
            }
        }
        return Result.success();
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
                            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to transfer out amount");
                        }
                        break;
                    case TRANSFER_IN:
                        if (balanceSnapshotManager.transferInToWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount())
                                != 1) {
                            log.error("failed to update snapshot to transfer in amount, reservation: {}, action: {}", snapshot, action);
                            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to transfer in amount");
                        }
                        break;
                }
            }
        }
        return Result.success();
    }

    // TODO remove this
    Result<Void> postLedger(TransactionInfo transactionInfo) {
//        if (true) {throw new RuntimeException();}
        List<String> walletIds = new ArrayList<>(transactionInfo.walletIdToPostActions.keySet());
        List<WalletAccountMapping> mappings = walletAccountMappingManager.selectInWalletIds(walletIds);
        if (mappings.size() != walletIds.size()) {
            log.error("wallet_account_mapping_not_found, wallet_ids: {}, mappings: {}", walletIds, mappings);
            return Result.fail(ErrorCode.WALLET_ACCOUNT_MAPPING_NOT_FOUND, "wallet_account_mapping_not_found");
        }
        Map<String, String> walletToAccount = mappings.stream().collect(Collectors.toMap(WalletAccountMapping::getWalletId, WalletAccountMapping::getAccountRefId));

        List<LedgerEntryPb> entries = new ArrayList<>();
        for (List<WalletAction> actions : transactionInfo.walletIdToPostActions.values()) {
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
        }

        PostTransactionRequestPb req = PostTransactionRequestPb.newBuilder()
                .setReferenceId(transactionInfo.walletTxn.getReferenceId())
                .addAllEntries(entries).build();
        Result<PostTransactionReplyPb> result = postServiceClient.postTransaction(req);
        if (!result.success) {
            log.error("failed to post transaction, req: {}, result: {}", req, result);
            return Result.fail(result);
        }
        return Result.success();
    }

    Result<WalletTransaction> afterPostingLedger(RequestInfo requestInfo, TransactionInfo transactionInfo) {
        List<BalanceSnapshot> snapshotIdsInOrder = requestInfo.snapshots.stream()
                .sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());

        Result<Void> outResult = DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            // should update all reservations here
            Result<Void> result = updateReservations(transactionInfo.reservations,
                    (reservation) -> updateReservationAfterPosting(reservation, transactionInfo.walletIdToPostActions));
            if (!result.success) {
                return Result.fail(result);
            }

            result = updateSnapshotsAfterPosting(transactionInfo.walletIdToPostActions, snapshotIdsInOrder);
            if (!result.success) {
                return Result.fail(result);
            }
            if (walletTransactionManager.updateTransactionStatus(transactionInfo.walletTxn.getId(), TransactionStatus.PENDING, TransactionStatus.COMPLETED)
                    != 1) {
                log.error("wallet_transaction_update_failed, transaction: {}", transactionInfo.walletTxn);
                return Result.fail(ErrorCode.WALLET_TRANSACTION_UPDATE_FAILED, "transaction_update_failed");
            }
            return Result.success();
        });
        if (!outResult.success) {
            return Result.fail(outResult);
        }
        // outbox should not block main flow
//        if (walletOutboxManager.finishOutbox(transactionInfo.outbox) != 1) {
//            log.error("finish outbox failed, outbox={}", transactionInfo.outbox);
//        }
        WalletTransaction txn = walletTransactionManager.selectById(transactionInfo.walletTxn.getId());
        if (txn == null) {
            log.error("wallet_transaction_not_found, transaction: {}", transactionInfo.walletTxn);
            return Result.fail(ErrorCode.WALLET_TRANSACTION_NOT_FOUND, "wallet_transaction_not_found");
        }
        return Result.success(txn);
    }



    WalletTransaction createTransaction(TransactionType transactionType, String referenceId, String idempotencyKey, RequestInfo requestInfo) {
        return WalletTransaction.create(
                IdGenerator.generateWalletTransactionId(),
                referenceId,
                requestInfo.serviceId,
                idempotencyKey,
                TransactionStatus.PENDING,
                transactionType,
                requestInfo.businessType
        );
    }

    WalletReservation createReservation(WalletTransaction walletTransaction, RequestInfo.Line line) {
        switch (line.operationTypePb) {
            case OperationTypePb_Debit:
                // in Atomic txn, reservation asset goes into pending settle after created
                return  WalletReservation.create(
                        IdGenerator.generateReservationId(),
                        walletTransaction.getInitiator(),
                        walletTransaction.getReferenceId() + ":" + line.walletId,
                        line.walletId,
                        line.walletRef,
                        line.assetCode,
                        line.amount,
                        0L,
                        0L,
                        line.amount,
                        0L,
                        ReservationStatus.ACTIVE,
                        ReservationOutcome.NOT_DONE,
                        walletTransaction.getTxnId()
                );
            case OperationTypePb_Reserve:
                return  WalletReservation.create(
                        IdGenerator.generateReservationId(),
                        walletTransaction.getInitiator(),
                        walletTransaction.getReferenceId() + ":" + line.walletId,
                        line.walletId,
                        line.walletRef,
                        line.assetCode,
                        line.amount,
                        line.amount,
                        0L,
                        0L,
                        0L,
                        ReservationStatus.ACTIVE,
                        ReservationOutcome.NOT_DONE,
                        walletTransaction.getTxnId()
                );
            default: throw new InvalidEnumException("OperationType_Reserve not supported: " + line.operationTypePb);
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

    private List<WalletAction> createActionsFromLine(String txnId, RequestInfo.Line line) {
        List<WalletAction> actions = new ArrayList<>();
        switch (line.operationTypePb) {
            case OperationTypePb_Reserve:
                actions.add(createAction(txnId, WalletBucket.AVAILABLE, ActionType.RESERVE, line));
                break;
            case OperationTypePb_Earmark:
                actions.add(createAction(txnId, WalletBucket.RESERVED, ActionType.CONSUME, line));
                actions.add(createAction(txnId, WalletBucket.RESERVED, ActionType.TRANSFER_OUT, line));
                break;
            case OperationTypePb_Release:
                actions.add(createAction(txnId, WalletBucket.RESERVED, ActionType.RELEASE, line));
                break;
            case OperationTypePb_Debit:
                actions.add(createAction(txnId, WalletBucket.AVAILABLE, ActionType.RESERVE, line));
                actions.add(createAction(txnId, WalletBucket.RESERVED, ActionType.CONSUME, line));
                actions.add(createAction(txnId, WalletBucket.RESERVED, ActionType.TRANSFER_OUT, line));
                break;
            case OperationTypePb_Credit:
                actions.add(createAction(txnId, WalletBucket.AVAILABLE, ActionType.TRANSFER_IN, line));
                break;
            default:
                throw new InvalidEnumException("invalid operation type: " + line.operationTypePb);
        }
        return actions;
    }

    private WalletAction createAction(String txnId, WalletBucket bucket, ActionType actionType, RequestInfo.Line line) {
        return WalletAction.create(
                IdGenerator.generateWalletActionId(),
                txnId,
                line.walletId,
                line.assetCode,
                bucket,
                actionType,
                line.amount,
                line.reservationId
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

    // memory cache, not real time data
    @AllArgsConstructor
    static class RequestInfo {
        final ServiceId serviceId;
        final BusinessType businessType;
        final TransactionType transactionType;
        final boolean needPostLedger;
        final List<BalanceSnapshot> snapshots;
//        final Map<OperationTypePb, List<BalanceSnapshot>> operationToSnapshots;
        // only have reservations in the request (created by other txn), not including txn created by this txn
        final List<WalletReservation> reservationsInTheRequest;
        final List<Line> lines;
        @AllArgsConstructor
        static class Line {
            String walletId;
            String walletRef;
            String assetCode;
            OperationTypePb operationTypePb;
            Long amount;
            String reservationId;
        }
    }

    // memory cache, not real time data
    @AllArgsConstructor
    static class TransactionInfo {
        final WalletTransaction walletTxn;
        final List<WalletAction> postAction; // only CONSUME, TRANSFER_OUT, TRANSFER_IN
        final Map<String, List<WalletAction>> walletIdToPostActions; // only CONSUME, TRANSFER_OUT, TRANSFER_IN
        final List<WalletReservation> reservations;
        final Outbox outbox;

        static TransactionInfo create(WalletTransaction txn, List<WalletAction> actions, List<WalletReservation> reservations, Outbox outbox) {
            Map<String, List<WalletAction>> walletIdToActions = new HashMap<>();
            for (WalletAction action : actions) {
                walletIdToActions.computeIfAbsent(action.getWalletId(), k -> new ArrayList<>());
                walletIdToActions.get(action.getWalletId()).add(action);
            }
            return new TransactionInfo(txn, actions, walletIdToActions, reservations, outbox);
        }
    }
}
