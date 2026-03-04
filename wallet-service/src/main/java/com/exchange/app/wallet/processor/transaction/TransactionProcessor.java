package com.exchange.app.wallet.processor.transaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.wallet.cronjob.outbox.constant.OutboxConstant;
import com.exchange.app.wallet.dao.repository.*;
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
import com.exchange.common.cache.client.IdempRedisClient;
import com.exchange.app.wallet.config.CustomCacheConfig;
import com.exchange.common.cache.utils.CommonIdempHelper;
import com.exchange.common.cache.utils.IdempValue;
import com.exchange.common.constant.GlobalServiceId;
import com.exchange.common.db.utils.DbTransactionHelper;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.StableHashHelper;
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
    private final IdempRedisClient idempRedisClient;

    private final CustomCacheConfig.Idemp idempConfig;

    private final BalanceSnapshotRepository balanceSnapshotManager;
    private final WalletReservationRepository walletReservationManager;
    private final WalletTransactionRepository walletTransactionManager;
    private final WalletActionRepository walletActionManager;
    private final WalletAccountMappingRepository walletAccountMappingManager;
    private final OutboxRepository outboxManager;
    private final DefaultPublisher postLedgerPublisher;

    // TODO temporary
    static final private String SCOPE = "post_transaction";

    // return txn_id if exists
    // return null if not exists
    Result<String> idempCheckAndReqValidate(RequestInfo requestInfo) {
        Result<Void> precheckResult = requestPrecheck(requestInfo);
        if (precheckResult.isFailed()) {
            // fast fail before claim idemp
            return Results.fail(precheckResult);
        }

        Result<IdempValue> valueResult = claimIdempCacheIfAbsent(requestInfo);
        // there should be no idemp value with the given token in the cache if fails here. no need to release here
        if (valueResult.isFailed()) {
            return Results.fail(valueResult);
        }

        IdempValue value = valueResult.value;
        // when value is not null, it means claim idemp fails because it has been claimed, no need to release here
        if (value != null) {
            switch (value.status) {
                case PENDING:
                    // another request with same params is in processing (but likely no wallet_txn has been persisted in db)
                    log.error("another same request in processing, requestInfo:{}, idempValue:{}", requestInfo, value);
                    return Results.fail(ErrorCode.REQUEST_IN_PROCESSING, "request still processing");
                case ACCEPTED:
                    // return txn id
                    return Results.success(value.content, "wallet txn already accepted");
                default:
                    log.error("unknown idemp state:{}", value);
                    return Results.fail(ErrorCode.INVALID_ENUM_ERROR, "unknown idemp state");
            }
        }

        WalletTransaction walletTxn = idempDbCheck(requestInfo.serviceIdPb, requestInfo.idempotenceKey);
        if (walletTxn != null) {
            // set idemp to DONE when found wallet txn
            idempRedisClient.markIdempDone(GlobalServiceId.WALLET.code, SCOPE, requestInfo.idempotenceKey,
                    String.valueOf(requestInfo.getStableHash()), requestInfo.token, walletTxn.getTxnId(), idempConfig.getDoneTtl());

            log.info("wallet txn already exists: {}", walletTxn);
            return Results.success(walletTxn.getTxnId(), "wallet txn already exists");
        }

        return Results.success();
    }

    // simply precheck, validate simple rules like enum and amount>0
    private Result<Void> requestPrecheck(RequestInfo requestInfo) {
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(requestInfo.serviceIdPb);
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            log.error("invalid service id, serviceId: {}", requestInfo.serviceIdPb);
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid service id: " + requestInfo.serviceIdPb);
        }
        BusinessType businessType = EnumPbMappers.businessTypePbMapper.to(requestInfo.businessTypePb);
        if (businessType == null || businessType == BusinessType.UNKNOWN) {
            log.error("invalid businessType, businessType: {}", requestInfo.businessTypePb);
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid business type: " + requestInfo.businessTypePb);
        }
        if (requestInfo.transactionType == null || requestInfo.transactionType == TransactionType.UNKNOWN) {
            log.error("invalid transactionType, transactionType: {}", requestInfo.transactionType);
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid transaction type: " + requestInfo.transactionType);
        }
        for (TransactionLinePb line : requestInfo.linePbs) {
            // validate action type and amount
            if (line.getOperationType() == OperationTypePb.UNRECOGNIZED || line.getOperationType() == OperationTypePb.OperationTypePb_Unknown) {
                log.error("invalid operationType, operationType: {}", line.getOperationType());
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid operation type: " + line);
            }
            if (line.getAmount() <= 0) {
                log.error("invalid amount, amount: {}", line.getAmount());
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid amount: " + line);
            }
        }
        return Results.success();
    }

    // return null if set success
    // return idemp info in the cache when fail to set
    private Result<IdempValue> claimIdempCacheIfAbsent(RequestInfo requestInfo) {
        String globalCode = GlobalServiceId.WALLET.code;
        String reqHash = requestInfo.getStableHash();
        String key = CommonIdempHelper.idempKey(globalCode, SCOPE, requestInfo.idempotenceKey);
        Boolean success = idempRedisClient.claimIdempIfAbsent(globalCode, SCOPE, requestInfo.idempotenceKey, reqHash, requestInfo.token, idempConfig.getPendingTtl());
        if (success) {
            return Results.success();
        }

        String idempV = idempRedisClient.getIdemp(globalCode, SCOPE, requestInfo.idempotenceKey);
        Result<IdempValue> parseResult = CommonIdempHelper.parseIdempValue(idempV);
        if (parseResult.isFailed()) {
            log.error("parse idemp value failed, key:{} , value:{} result:{}", key, idempV, parseResult);
            // rare case, delete invalid value to self-recover.
            // force delete here, value is malformed and possibly cannot get a token to verify the owner.
            idempRedisClient.forceDeleteIdemp(globalCode, SCOPE, requestInfo.idempotenceKey);
            // return null then access db to check if exists
            return Results.success();
        }

        IdempValue idempValue = parseResult.value;
        if (!Objects.equals(idempValue.hash, reqHash)) {
            log.error("idemp value hashcode conflict, key:{}, value:{}, hash:{}, reqInfo: {}", key, idempValue, reqHash, requestInfo);
            return Results.fail(ErrorCode.REQUEST_HASH_CONFLICT, "idemp value hashcode conflict");
        }

        return Results.success(idempValue);
    }

    private WalletTransaction idempDbCheck(ServiceIdPb serviceIdPb, String idempotencyKey) {
        // TODO add hash check here
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(serviceIdPb);
        return walletTransactionManager.selectByIdempotencyKey(serviceId, idempotencyKey);
    }

    // reserve -> snapshot: -available, +reserved
    // consume -> reservation: -remaining, +pending_settle
    // release -> snapshot, reservation: -reserved, +available | -pending_settle, +released, change status
    // package private
    // need to release the claimed idemp key if token matches
    Result<TransactionInfo> beforePostingLedger(RequestInfo requestInfo, boolean needPostLedger) {
        WalletTransaction walletTxn = createTxn(requestInfo);

        // complete txn immediately if no need to post ledger
        if (!needPostLedger) {
            walletTxn.setTxnStatus(TransactionStatus.COMPLETED);
        }

        // get snapshot from db
        Set<String> walletRefs = requestInfo.linePbs.stream().distinct()
                .map(TransactionLinePb::getWalletRef)
                .collect(Collectors.toSet());
        List<BalanceSnapshot> balanceSnapshots = balanceSnapshotManager.selectByRefs(walletTxn.getInitiator(), new ArrayList<>(walletRefs));
        Map<String, BalanceSnapshot> walletRefToSnapshot = balanceSnapshots.stream().collect(Collectors.toMap(BalanceSnapshot::getWalletReferenceId, snapshot -> snapshot));

        // get reservation from db
        Set<String> reservationRefs = requestInfo.linePbs.stream()
                .filter((line)-> !Strings.isEmpty(line.getReservationRef()))
                .distinct().map(TransactionLinePb::getReservationRef).collect(Collectors.toSet());
        List<WalletReservation> reservationsInRequest = new ArrayList<>();
        if (!reservationRefs.isEmpty()) {
            reservationsInRequest = walletReservationManager.selectByRefs(new ArrayList<>(reservationRefs));
            if (reservationsInRequest.size() != reservationRefs.size()) {
                log.error("reservations not found, requested_reservation: {}, reservation_in_db: {}", reservationRefs, reservationsInRequest);
                releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
                return Results.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservations not found");
            }
        }
        Map<String, WalletReservation> refToReservation = reservationsInRequest.stream().collect(Collectors.toMap(WalletReservation::getReferenceId, reservation->reservation));

        // create action and reservation
        List<WalletAction> actions = new ArrayList<>();
        List<WalletReservation> createdReservations = new ArrayList<>();
        for (TransactionLinePb line : requestInfo.linePbs) {
            BalanceSnapshot snapshot = walletRefToSnapshot.get(line.getWalletRef());
            if (snapshot == null) {
                log.error("snapshot not found, line: {}", line);
                releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
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
                        releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
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
            releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
            return Results.fail(result);
        }

        Outbox outbox;
        if (needPostLedger) {
            Result<Outbox> outboxResult = createOutbox(walletTxn, actions);
            if (outboxResult.isFailed()) {
                log.error("createOutbox failed: {}", outboxResult);
                releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
                return Results.fail(outboxResult);
            }
            outbox = outboxResult.value;
        } else {
            outbox = null;
        }

        result = updateDbToCreateTxn(walletTxn, actions, balanceSnapshots, reservationsInRequest, createdReservations, outbox);
        if (result.isFailed()) {
            log.error("updateDbToCreateTxn failed: {}", result);
            releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
            return Results.fail(result);
        }
        // set idemp to DONE when found wallet txn
        idempRedisClient.markIdempDone(GlobalServiceId.WALLET.code, SCOPE, requestInfo.idempotenceKey,
                String.valueOf(requestInfo.getStableHash()), requestInfo.token, walletTxn.getTxnId(), idempConfig.getDoneTtl());

        if (needPostLedger) {
            Result<Void> publishResult = postLedgerPublisher.publish(outbox);
            if (publishResult.success) {
                if (outboxManager.updateStatusToFinalize(outbox, OutboxStatus.SENT, outbox.getLastAttemptAt())
                        == 1) {
                    outbox.setOutboxStatus(OutboxStatus.SENT);
                    outbox.setFinalizedAt(outbox.getLastAttemptAt());
                } else {
                    // do not block the main flow
                    log.error("finalize outbox failed, {}", outbox);
                }
            } else {
                // publish failed should not block main flow, and should return txn execute successfully
                log.error("post ledger publish failed, outbox: {}, result: {}", outbox, publishResult);
            }
        }

        return Results.success(TransactionInfo.create(walletTxn, actions, new ArrayList<>(refToReservation.values()), balanceSnapshots));
    }

    private void releaseIdempBeforeReturnError(String IdempKey, String hash, String token) {
        String idempV = CommonIdempHelper.idempPendingValue(hash, token);
        Result<String> releaseResult = idempRedisClient.releaseIdempIfOwned(GlobalServiceId.WALLET.code, SCOPE, IdempKey, idempV);
        if (releaseResult.isFailed()) {
            // log the error
            log.error("release idemp failed, key:{} , result:{}", IdempKey, releaseResult);
        }
    }

    private WalletTransaction createTxn(RequestInfo requestInfo) {
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(requestInfo.serviceIdPb);
        BusinessType businessType = EnumPbMappers.businessTypePbMapper.to(requestInfo.businessTypePb);

        return createTransaction(requestInfo.transactionType, requestInfo.referenceId, serviceId, requestInfo.idempotenceKey, businessType);
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
                // TODO maybe get txn and return if info match
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

        // only validate CONSUME/TRANSFER_IN/TRANSFER_OUT actions, only CONSUME have reservation
        Result<Void> result = validateActionInfo(afterPostingActions, snapshots, consumeReservations);
        if (result.isFailed()) {
            log.error("validate action after get txn failed, result: {}", result);
            return Results.fail(result);
        }

        TransactionInfo transactionInfo = TransactionInfo.create(txn, afterPostingActions, consumeReservations, snapshots);
        return Results.success(transactionInfo);
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

    static class RequestInfo {
        final String referenceId;
        final ServiceIdPb serviceIdPb;
        final String idempotenceKey;
        final BusinessTypePb businessTypePb;
        final TransactionType transactionType;
        final List<TransactionLinePb> linePbs;
        final String requestName;

        // token is uuid (which is random) and not included in stable hash computation
        final String token;

        private String stableHash;

        RequestInfo(String referenceId, ServiceIdPb serviceIdPb, String idempotenceKey, BusinessTypePb businessTypePb, TransactionType transactionType, List<TransactionLinePb> linePbs, String requestName, String token) {
            this.referenceId = referenceId;
            this.serviceIdPb = serviceIdPb;
            this.idempotenceKey = idempotenceKey;
            this.businessTypePb = businessTypePb;
            this.transactionType = transactionType;
            this.linePbs = linePbs;
            this.requestName = requestName;
            this.token = token;
        }

        String getStableHash() {
            if (!Strings.isEmpty(stableHash)) {
                return stableHash;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(referenceId).append("|")
                    .append(serviceIdPb).append("|")
                    .append(idempotenceKey).append("|")
                    .append(businessTypePb).append("|")
                    .append(transactionType).append("|")
                    .append(requestName);
            List<TransactionLinePb> sorted = new ArrayList<>(linePbs);
            // sort lines then append together. same line list in random order will have same hashcode
            sorted.sort(Comparator.comparing(TransactionLinePb::getWalletRef)
                    .thenComparing(TransactionLinePb::getAssetCode)
                    .thenComparingInt(TransactionLinePb::getOperationTypeValue)
                    .thenComparingLong(TransactionLinePb::getAmount)
                    .thenComparing(TransactionLinePb::getReservationRef));
            for (TransactionLinePb linePb : sorted) {
                sb.append(linePb.getWalletRef()).append("|")
                        .append(linePb.getAssetCode()).append("|")
                        .append(linePb.getOperationTypeValue()).append("|")
                        .append(linePb.getAmount()).append("|")
                        .append(linePb.getReservationRef());
            }
            stableHash = StableHashHelper.stableHash(sb.toString());
            return stableHash;
        }
    }

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