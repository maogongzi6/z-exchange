package com.exchange.app.wallet.processor.transaction.step;

import com.exchange.app.wallet.cronjob.outbox.constant.OutboxConstant;
import com.exchange.app.wallet.dao.repository.*;
import com.exchange.app.wallet.kafka.producer.DefaultPublisher;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.po.wallet.WalletAccountMapping;
import com.exchange.app.wallet.processor.transaction.model.RequestInfo;
import com.exchange.app.wallet.processor.transaction.model.TransactionInfo;
import com.exchange.app.wallet.processor.transaction.util.Constant;
import com.exchange.app.wallet.processor.transaction.util.ObjectBuilder;
import com.exchange.app.wallet.processor.transaction.util.ValidateHelper;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.*;
import com.exchange.common.redis.idemp.IdempRedisClient;
import com.exchange.app.wallet.config.CustomCacheProperties;
import com.exchange.common.redis.idemp.utils.CommonIdempHelper;
import com.exchange.common.constant.GlobalServiceId;
import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.time.LocalDateTimeHelper;
import com.exchange.proto.wallet.wallet.TransactionLinePb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@EnableConfigurationProperties(CustomCacheProperties.Idemp.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BeforePostLedgerProcessor {
    private final DbTxnExecutor dbTxnExecutor;
    private final IdempRedisClient idempRedisClient;

    private final CustomCacheProperties.Idemp idempConfig;

    private final BalanceSnapshotRepository balanceSnapshotManager;
    private final WalletReservationRepository walletReservationManager;
    private final WalletTransactionRepository walletTransactionManager;
    private final WalletActionRepository walletActionManager;
    private final WalletAccountMappingRepository walletAccountMappingManager;
    private final OutboxRepository outboxManager;
    private final DefaultPublisher postLedgerPublisher;
    private final UpdateReservationProcessor updateReservationProcessor;

    // reserve -> snapshot: -available, +reserved
    // consume -> reservation: -remaining, +pending_settle
    // release -> snapshot, reservation: -reserved, +available | -pending_settle, +released, change status
    // package private
    // need to release the claimed idemp key if token matches
    public Result<TransactionInfo> beforePostingLedger(RequestInfo requestInfo, boolean needPostLedger) {
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
                    reservation = ObjectBuilder.createReservation(walletTxn, snapshot.getWalletId(), line);
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
            List<WalletAction> actionsFromLine = ObjectBuilder.createActionsFromLine(walletTxn.getTxnId(), snapshot.getWalletId(), reservationId, line);
            actions.addAll(actionsFromLine);
        }

        //validate
        Result<Void> result = ValidateHelper.validateActionInfo(actions, balanceSnapshots, new ArrayList<>(refToReservation.values()));
        if (result.isFailed()) {
            log.error("validate action info failed, result: {}", result);
            releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
            return Results.fail(result);
        }

        Result<Outbox> outboxResult = createOutboxIfNeeded(walletTxn, actions, requestInfo, needPostLedger);
        if (!outboxResult.success()) {
            releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
            return Results.fail(outboxResult);
        }
        Outbox outbox = outboxResult.value();


        result = updateDbToCreateTxn(walletTxn, actions, balanceSnapshots, reservationsInRequest, createdReservations, outbox);
        if (result.isFailed()) {
            log.error("updateDbToCreateTxn failed: {}", result);
            releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
            return Results.fail(result);
        }
        // set idemp to DONE when found wallet txn
        idempRedisClient.markIdempDone(GlobalServiceId.WALLET.code, Constant.SCOPE, requestInfo.idempotenceKey,
                String.valueOf(requestInfo.getStableHash()), requestInfo.token, walletTxn.getTxnId(), idempConfig.getDoneTtl());

        if (needPostLedger) {
            postLedger(outbox);
        }

        return Results.success(TransactionInfo.create(walletTxn, actions, new ArrayList<>(refToReservation.values()), balanceSnapshots));
    }

    private void releaseIdempBeforeReturnError(String IdempKey, String hash, String token) {
        String idempV = CommonIdempHelper.idempPendingValue(hash, token);
        Result<String> releaseResult = idempRedisClient.releaseIdempIfOwned(GlobalServiceId.WALLET.code, Constant.SCOPE, IdempKey, idempV);
        if (releaseResult.isFailed()) {
            // log the error
            log.error("release idemp failed, key:{} , result:{}", IdempKey, releaseResult);
        }
    }

    private WalletTransaction createTxn(RequestInfo requestInfo) {
        ServiceId serviceId = EnumPbMappers.serviceIdPbMapper.to(requestInfo.serviceIdPb);
        BusinessType businessType = EnumPbMappers.businessTypePbMapper.to(requestInfo.businessTypePb);

        return ObjectBuilder.createTransaction(requestInfo.transactionType, requestInfo.referenceId, serviceId, requestInfo.idempotenceKey, businessType);
    }

    private Result<Void> updateDbToCreateTxn(WalletTransaction walletTxn, List<WalletAction> actions, List<BalanceSnapshot> balanceSnapshots,
                                              List<WalletReservation> reservationsInRequest, List<WalletReservation> createdReservations, Outbox outbox) {

        Map<String, List<WalletAction>> walletIdToActions = new HashMap<>();
        for (WalletAction action : actions) {
            walletIdToActions.computeIfAbsent(action.getWalletId(), k -> new ArrayList<>());
            walletIdToActions.get(action.getWalletId()).add(action);
        }

        // immediately claim the outbox and attempt to publish it right after update db
        return dbTxnExecutor.executeWithDefault(() -> {
            Result<Void> result = updateReservationProcessor.updateReservations(reservationsInRequest,
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

    // TODO for db txn updates, uses select for update to lock records, then update it
    //  this is safer in multi-instance scenario,
    //  and also good for cache refreshing
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

    private Result<Outbox> createOutboxIfNeeded(WalletTransaction walletTxn, List<WalletAction> actions, RequestInfo requestInfo, boolean needPostLedger) {
        Outbox outbox = null;
        if (needPostLedger) {
            List<WalletAction> transferActions = actions.stream()
                    .filter((action) -> action.getActionType() == ActionType.TRANSFER_IN || action.getActionType() == ActionType.TRANSFER_OUT).collect(Collectors.toList());

            List<String> walletIds = actions.stream()
                    .map(WalletAction::getWalletId)
                    .distinct()
                    .collect(Collectors.toList());
            List<WalletAccountMapping> mappings = walletAccountMappingManager.selectInWalletIds(walletIds);
            if (mappings.size() != walletIds.size()) {
                log.error("wallet_account_mapping_not_found, wallet_ids: {}, mappings: {}", walletIds, mappings);
                return Results.fail(ErrorCode.WALLET_ACCOUNT_MAPPING_NOT_FOUND, "wallet_account_mapping_not_found");
            }
            Result<Outbox> outboxResult = ObjectBuilder.createOutbox(walletTxn, actions, mappings);
            if (outboxResult.isFailed()) {
                log.error("createOutbox failed: {}", outboxResult);
                releaseIdempBeforeReturnError(requestInfo.idempotenceKey, requestInfo.getStableHash(), requestInfo.token);
                return Results.fail(outboxResult);
            }
            outbox = outboxResult.value();
        }
        return Results.success(outbox);
    }

    private void postLedger(Outbox outbox) {
        Result<Void> publishResult = postLedgerPublisher.publish(outbox);
        if (publishResult.success()) {
            if (outboxManager.updateStatusToFinalize(outbox, OutboxStatus.SENT, outbox.getLastAttemptAt())
                    == 1) {
                outbox.setOutboxStatus(OutboxStatus.SENT);
                outbox.setFinalizedAt(outbox.getLastAttemptAt());
            } else {
                // do not block the main flow
                log.error("finalize outbox failed, {}", outbox);
            }
        } else {
            // publish failed should not block the main flow and should return txn execute successfully
            log.error("post ledger publish failed, outbox: {}, result: {}", outbox, publishResult);
        }
    }

}