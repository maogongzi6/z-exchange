package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.manager.*;
import com.exchange.app.wallet.exception.InvalidEnumException;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletBucket;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.enums.outbox.OutboxEventType;
import com.exchange.app.wallet.po.enums.outbox.OutboxStatus;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.outbox.WalletOutbox;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.DbTransactionHelper;
import com.exchange.app.wallet.utils.EnumMappers;
import com.exchange.app.wallet.utils.IdGenerator;
import com.exchange.app.wallet.utils.OutboxHelper;
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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
// package private
class TransactionProcessor {
    private final BalanceSnapshotManager balanceSnapshotManager;
    private final TransactionTemplate transactionTemplate;
    private final WalletReservationManager walletReservationManager;
    private final WalletTransactionManager walletTransactionManager;
    private final WalletActionManager walletActionManager;
    private final WalletOutboxManager walletOutboxManager;

    Result<RequestInfo> validateAndGetRequestInfo(ServiceIdPb serviceIdPb, BusinessTypePb businessTypePb, List<TransactionLinePb> linePbs) {
        ServiceId serviceId = EnumMappers.serviceIdPbMapper.to(serviceIdPb);
        BusinessType businessType = EnumMappers.businessTypePbMapper.to(businessTypePb);
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid service id: " + serviceIdPb);
        }
        if (businessType == null || businessType == BusinessType.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid business type: " + businessTypePb);
        }
        Result<Void> result = validateAssetInfo(linePbs);
        if (!result.success) {
            return Result.fail(result);
        }

        // get reservation from db to do precheck
        Set<String> reservationRefs = linePbs.stream()
                .filter((reservation)-> !Strings.isEmpty(reservation.getReservationRef()))
                .distinct().map(TransactionLinePb::getReservationRef).collect(Collectors.toSet());
        List<WalletReservation> reservations = new ArrayList<>();
        if (!reservationRefs.isEmpty()) {
            reservations = walletReservationManager.selectByRefs(new ArrayList<>(reservationRefs));
        }
        Map<String, WalletReservation> refToReservation = reservations.stream().collect(Collectors.toMap(WalletReservation::getReservationId, reservation->reservation));

        // get snapshot from db to do precheck
        Set<String> walletRefs = linePbs.stream().distinct().map(TransactionLinePb::getWalletRef).collect(Collectors.toSet());
        List<BalanceSnapshot> balanceSnapshots = balanceSnapshotManager.selectByRefs(serviceId, new ArrayList<>(walletRefs));
        Map<String, BalanceSnapshot> walletRefToSnapshot = balanceSnapshots.stream().collect(Collectors.toMap(BalanceSnapshot::getWalletReferenceId, snapshot -> snapshot));
        Map<OperationTypePb, List<BalanceSnapshot>> operationToSnapshots = new EnumMap<>(OperationTypePb.class);
        List<RequestInfo.Line> lines = new ArrayList<>();
        for (TransactionLinePb line : linePbs) {
            BalanceSnapshot snapshot = walletRefToSnapshot.get(line.getWalletRef());
            result = validateSnapshot(snapshot, line);
            if (!result.success) {
                return Result.fail(result);
            }

            String reservationId = null;
            if (!Strings.isEmpty(line.getReservationRef())) {
                WalletReservation reservation = refToReservation.get(line.getReservationRef());
                result = validateReservation(reservation, snapshot, line);
                if (!result.success) {
                    return Result.fail(result);
                }
                reservationId = reservation.getReservationId();
            }

            operationToSnapshots.computeIfAbsent(line.getOperationType(), k -> new ArrayList<>());
            operationToSnapshots.get(line.getOperationType()).add(snapshot);
            lines.add(new RequestInfo.Line(snapshot.getWalletId(), line.getWalletRef(), line.getAssetCode(), line.getOperationType(), line.getAmount(), reservationId));
        }

        return Result.success(new RequestInfo(serviceId, businessType, balanceSnapshots, operationToSnapshots, reservations, lines));
    }

    private Result<Void> validateAssetInfo(List<TransactionLinePb> linePbs) {
        Map<String, Long> inAssetToAmount = new HashMap<>(), outAssetToAmount = new HashMap<>();
        for (TransactionLinePb line : linePbs) {
            // validate action type and amount
            if (line.getOperationType() == OperationTypePb.UNRECOGNIZED || line.getOperationType() == OperationTypePb.OperationType_Unknown) {
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
                if (!targetAssetToAmount.containsKey(line.getAssetCode())) {
                    targetAssetToAmount.put(line.getAssetCode(), 0L);
                }
                targetAssetToAmount.put(line.getAssetCode(), line.getAmount() + line.getAmount());
            }
        }

        if (inAssetToAmount.size() != outAssetToAmount.size()) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "abnormal lines, asset types not match");
        }
        // validate: for each asset, transfer in should match transfer out
        for (Map.Entry<String, Long> entry : inAssetToAmount.entrySet()) {
            String inAssetCode = entry.getKey();
            Long inAmount = entry.getValue();
            if (!Objects.equals(inAmount, outAssetToAmount.get(inAssetCode))) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced asset: " + inAssetCode + ", in_amount: " + inAmount + ", out_amount" + outAssetToAmount.get(inAssetCode));
            }
        }
        return Result.success();
    }

    private boolean isTransfer(OperationTypePb operationTypePb) {
        return isTransferIn(operationTypePb) || isTransferOut(operationTypePb);
    }

    private boolean isTransferOut(OperationTypePb operationTypePb) {
        return operationTypePb == OperationTypePb.OperationType_Earmark || operationTypePb == OperationTypePb.OperationType_Debit;
    }

    private boolean isTransferIn(OperationTypePb operationTypePb) {
        return operationTypePb == OperationTypePb.OperationType_Credit;
    }

    private Result<Void> validateSnapshot(BalanceSnapshot snapshot, TransactionLinePb line) {
        if (snapshot == null) {
            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "snapshot not found, line: " + line);
        }
        if (!Objects.equals(snapshot.getWalletReferenceId(), line.getWalletRef()) || !Objects.equals(snapshot.getAssetId(), line.getAssetCode())) {
            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_MISMATCH, String.format("snapshot mismatch, snapshot: %s, line: %s", snapshot, line));
        }
        if (snapshot.getWalletStatus() != WalletStatus.OPEN) {
            return Result.fail(ErrorCode.WALLET_NOT_AVAILABLE, "wallet_status: " + snapshot.getWalletStatus());
        }
        return Result.success();
    }

    private Result<Void> validateReservation(WalletReservation reservation, BalanceSnapshot snapshot, TransactionLinePb line) {
        if (reservation == null) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation not found: " + line);
        }
        if (!Objects.equals(reservation.getWalletId(), snapshot.getWalletId()) || !Objects.equals(reservation.getAssetId(), line.getAssetCode())) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, String.format("reservation mismatch, reservation: %s, snapshot: %s, line: %s", reservation, snapshot, line));
        }
        if (reservation.getReservationStatus() != ReservationStatus.ACTIVE) {
            return Result.fail(ErrorCode.WALLET_NOT_AVAILABLE, String.format("reservation mismatch, reservation: %s, snapshot: %s, line: %s", reservation, snapshot, line));
        }
        return Result.success();
    }

    // reserve -> snapshot: -available, +reserved
    // consume -> reservation: -remaining, +pending_settle
    // release -> snapshot, reservation: -reserved, +available | -pending_settle, +released, change status
    Result<Void> beforePostLedger(WalletTransaction walletTxn, List<WalletAction> actions, List<WalletReservation> createReservations, WalletOutbox walletOutbox, TransactionProcessor.RequestInfo requestInfo) {
        // map wallet id to reserve action
        Map<String, List<WalletAction>> walletIdToActions = new HashMap<>();
        for (WalletAction action : actions) {
            walletIdToActions.computeIfAbsent(action.getWalletId(), k -> new ArrayList<>());
            walletIdToActions.get(action.getWalletId()).add(action);
        }

        return DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            if (!requestInfo.reservations.isEmpty()) {
                Result<Void> result = updateReservations(walletIdToActions, requestInfo.reservations);
                if (!result.success) {
                    return Result.fail(result);
                }
            }

            Result<Void> result = updateSnapshots(walletIdToActions, requestInfo.snapshots);
            if (!result.success) {
                return Result.fail(result);
            }

            if (walletReservationManager.batchInsert(createReservations) != createReservations.size()) {
                return Result.fail(ErrorCode.WALLET_RESERVATION_DUPLICATED, "unexpected reservation duplicated");
            }
            if (walletTransactionManager.insertIgnore(walletTxn) == 0) {
                return Result.fail(ErrorCode.WALLET_TRANSACTION_DUPLICATED, "unexpected transaction duplicated");
            }
            if (walletActionManager.batchInsert(actions) != actions.size()) {
                return Result.fail(ErrorCode.WALLET_ACTION_DUPLICATION, "unexpected action duplicated");
            }
            if (walletOutboxManager.insertIgnore(walletOutbox) == 0) {
                return Result.fail(ErrorCode.WALLET_OUTBOX_DUPLICATED, "unexpected outbox duplicated");
            }
            return Result.success();
        });
    }

    private Result<Void> updateReservations(Map<String, List<WalletAction>> walletIdToActions, List<WalletReservation> reservations) {
        List<Long> modifyReservationIds = reservations.stream().map(WalletReservation::getId).collect(Collectors.toList());
        // select for update to lock the rows in PK order
        var reservationFromDb = walletReservationManager.selectInIdForUpdate(modifyReservationIds);
        if (reservationFromDb.size() != modifyReservationIds.size()) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, String.format("wallet_reservation_not_found, ids: %s, reservationFromDb: %s", modifyReservationIds, reservationFromDb));
        }

        for (WalletReservation reservation : reservationFromDb) {
            WalletReservation copy = new WalletReservation();
            BeanUtils.copyProperties(reservation, copy);
            boolean changed = false;
            for (WalletAction action : walletIdToActions.get(reservation.getWalletId())) {
                changed = action.getActionType() == ActionType.CONSUME || action.getActionType() == ActionType.RELEASE;
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
            if (changed) {
                if (walletReservationManager.updateWithOptimisticLock(reservation, copy) != 1) {
                    return Result.fail(ErrorCode.WALLET_RESERVATION_UPDATE_FAILED, String.format("failed to update reservation, reservation: %s, copy: %s", reservation, copy));
                }
            }
        }
        return Result.success();
    }

    private Result<Void> updateSnapshots(Map<String, List<WalletAction>> walletIdToActions, List<BalanceSnapshot> snapshots) {
        List<BalanceSnapshot> snapshotInOrder = snapshots.stream().sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());
        // update snapshot to reserve/release money by id order
        // snapshotInOrder is memory cache, not real time data, only use wallet_id and id here
        for (BalanceSnapshot snapshot : snapshotInOrder) {
            // no need to combine update for one snapshot, basically there should only be one RESERVE or RELEASE for one snapshot for now
            // TODO maybe combine update in the future
            for (WalletAction action : walletIdToActions.get(snapshot.getWalletId())) {
                switch (action.getActionType()) {
                    case RESERVE:
                        if (balanceSnapshotManager.reserveFromWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount()) == 0) {
                            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to reserve amount");
                        }
                        break;
                    case RELEASE:
                        if (balanceSnapshotManager.releaseFromWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount()) == 0) {
                            return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to release amount");
                        }
                }
            }
        }
        return Result.success();
    }

    WalletTransaction createTransaction(TransactionType transactionType, String referenceId, String idempotencyKey, TransactionProcessor.RequestInfo requestInfo) {
        return WalletTransaction.create(
                IdGenerator.generateWalletTransactionId(),
                referenceId,
                requestInfo.serviceId,
                referenceId,
                TransactionStatus.PENDING,
                transactionType,
                requestInfo.businessType
        );
    }

    // in Atomic txn, reservation asset goes into pending settle after created
    WalletReservation createAtomicReservation(WalletTransaction walletTransaction, RequestInfo.Line line) {
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
    }

    WalletReservation createReservation(WalletTransaction walletTransaction, RequestInfo.Line line) {
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
    }

    WalletAction createReserveAction(WalletTransaction walletTxn, RequestInfo.Line line) {
        return createAction(walletTxn.getTxnId(), line, WalletBucket.AVAILABLE, ActionType.RESERVE);
    }

    WalletAction createConsumeAction(WalletTransaction walletTxn, RequestInfo.Line line, String reservationId) {
        return createAction(walletTxn.getTxnId(), line, WalletBucket.RESERVED, ActionType.CONSUME);
    }

    private WalletAction createTransferAction(WalletTransaction walletTxn, RequestInfo.Line line) {
        WalletBucket bucket;
        ActionType actionType;
        switch (line.operationTypePb) {
            case OperationType_Debit:
                bucket = WalletBucket.RESERVED;
                actionType = ActionType.TRANSFER_OUT;
                break;
            case OperationType_Credit:
                bucket = WalletBucket.AVAILABLE;
                actionType = ActionType.TRANSFER_IN;
                break;
            default:
                throw new InvalidEnumException("invalid operation type: " + line.operationTypePb);
        }
        return createAction(walletTxn.getTxnId(), line, bucket, actionType);
    }

    private WalletAction createAction(String txnId, RequestInfo.Line line, WalletBucket bucket, ActionType actionType) {
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

    WalletOutbox createOutbox(WalletTransaction walletTransaction, RequestInfo requestInfo) {
        return WalletOutbox.create(OutboxEventType.LEDGER_POST,
                walletTransaction.getReferenceId(),
                walletTransaction.getReferenceId(),
                OutboxStatus.PENDING,
                //TODO payload
                "{\"TODO\":\"todo\"}",
                0,
                OutboxHelper.nextAttempt()
        );
    }

    @AllArgsConstructor
    static class RequestInfo {
        final ServiceId serviceId;
        final BusinessType businessType;
        // memory cache, not real time data
        final List<BalanceSnapshot> snapshots;
        final Map<OperationTypePb, List<BalanceSnapshot>> operationToSnapshots;
        // memory cache, not real time data
        final List<WalletReservation> reservations;
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
}
