package com.exchange.app.wallet.processor.transaction.step;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.wallet.dao.repository.*;
import com.exchange.app.wallet.po.enums.transaction.ActionType;
import com.exchange.app.wallet.po.enums.transaction.ReservationStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.processor.transaction.model.TransactionInfo;
import com.exchange.app.wallet.processor.transaction.util.ValidateHelper;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AfterPostLedgerProcessor {
    private final DbTxnExecutor dbTxnExecutor;

    private final BalanceSnapshotRepository balanceSnapshotManager;
    private final WalletReservationRepository walletReservationManager;
    private final WalletTransactionRepository walletTransactionManager;
    private final WalletActionRepository walletActionManager;
    private final UpdateReservationProcessor updateReservationProcessor;

    public Result<WalletTransaction> afterPostLedger(String txnId) {
        Result<TransactionInfo> txnInfoResult = getTxnInfo(txnId);
        if (txnInfoResult.isFailed()) {
            log.error("failed to find transaction info, txnId: {}, result: {}", txnId, txnInfoResult);
            return Result.failure(txnInfoResult);
        }
        TransactionInfo txnInfo = txnInfoResult.getValue();
        if (txnInfo.walletTxn.hasFinalized()) {
            log.info("wallet txn has finalized, txnId: {}", txnId);
            return Result.success(txnInfo.walletTxn);
        }

        List<BalanceSnapshot> snapshotIdsInOrder = txnInfo.snapshots.stream()
                .sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());

        Result<Void> outResult = dbTxnExecutor.executeWithDefault(() -> {
            // should update all reservations here
            Result<Void> result = updateReservationProcessor.updateReservations(txnInfo.reservations,
                    (reservation) -> updateReservationAfterPosting(reservation, txnInfo.walletIdToPostActions));
            if (result.isFailed()) {
                return Result.failure(result);
            }

            result = updateSnapshotsAfterPosting(txnInfo.walletIdToPostActions, snapshotIdsInOrder);
            if (result.isFailed()) {
                return Result.failure(result);
            }
            if (walletTransactionManager.updateTransactionStatus(txnInfo.walletTxn, txnInfo.walletTxn.getId(), TransactionStatus.COMPLETED)
                    != 1) {
                log.error("wallet_transaction_update_failed, transaction: {}", txnInfo.walletTxn);
                return Result.failure(WalletServiceErrorCode.WALLET_TRANSACTION_UPDATE_FAILED, "transaction_update_failed");
            }
            return Result.success();
        });
        if (outResult.isFailed()) {
            return Result.failure(outResult);
        }
        WalletTransaction txn = walletTransactionManager.selectById(txnInfo.walletTxn.getId());
        if (txn == null) {
            log.error("wallet_transaction_not_found, transaction: {}", txnInfo.walletTxn);
            return Result.failure(WalletServiceErrorCode.WALLET_TRANSACTION_NOT_FOUND, "wallet_transaction_not_found");
        }
        return Result.success(txn);
    }

    private Result<TransactionInfo> getTxnInfo(String txnId) {
        WalletTransaction txn = walletTransactionManager.selectByTxnId(txnId);
        if (txn == null) {
            log.error("getTxnInfo failed, {}", txnId);
            return Result.failure(WalletServiceErrorCode.WALLET_TRANSACTION_NOT_FOUND, "no txn found for txnId: " + txnId);
        }

        List<WalletAction> afterPostingActions = walletActionManager.selectByTxnId(txn.getTxnId(), new LambdaQueryWrapper<>() {{
            in(WalletAction::getActionType, ActionType.TRANSFER_IN, ActionType.TRANSFER_OUT, ActionType.CONSUME);
        }});

        List<String> walletIds = afterPostingActions.stream().map(WalletAction::getWalletId).distinct().collect(Collectors.toList());
        List<BalanceSnapshot> snapshots = balanceSnapshotManager.selectByWalletIds(walletIds);
        if (snapshots.size() != walletIds.size()) {
            log.error("getTxnInfo failed, snapshot not found, txn: {}, walletIds: {}, snapshotsInDb: {}", txnId, walletIds, snapshots);
            return Result.failure(WalletServiceErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "snapshot not found");
        }

        List<WalletAction> consumeActions = afterPostingActions.stream().filter(action -> action.getActionType() == ActionType.CONSUME).collect(Collectors.toList());
        List<WalletReservation> consumeReservations = new ArrayList<>();
        if (!consumeActions.isEmpty()) {
            List<String> consumeReservationRefs = consumeActions.stream().map(WalletAction::getReservationId).distinct().collect(Collectors.toList());
            consumeReservations = walletReservationManager.selectByRefs(consumeReservationRefs);
            if (consumeReservationRefs.size() != consumeReservations.size()) {
                log.error("reservation size mismatch, reservationFromDb: {}, reservationRefs: {}", consumeReservationRefs, consumeReservations);
                return Result.failure(WalletServiceErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation size mismatch");
            }
        }

        // only validate CONSUME/TRANSFER_IN/TRANSFER_OUT actions, only CONSUME have reservation
        Result<Void> result = ValidateHelper.validateActionInfo(afterPostingActions, snapshots, consumeReservations);
        if (result.isFailed()) {
            log.error("validate action after get txn failed, result: {}", result);
            return Result.failure(result);
        }

        TransactionInfo transactionInfo = TransactionInfo.create(txn, afterPostingActions, consumeReservations, snapshots);
        return Result.success(transactionInfo);
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
                            return Result.failure(WalletServiceErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to transfer out amount");
                        }
                        break;
                    case TRANSFER_IN:
                        if (balanceSnapshotManager.transferInToWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount())
                                != 1) {
                            log.error("failed to update snapshot to transfer in amount, reservation: {}, action: {}", snapshot, action);
                            return Result.failure(WalletServiceErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update snapshot to transfer in amount");
                        }
                        break;
                }
            }
        }
        return Result.success();
    }

    private Result<WalletReservation> updateReservationAfterPosting(WalletReservation reservation, Map<String, List<WalletAction>> walletIdToActions) {
        for (WalletAction action : walletIdToActions.get(reservation.getWalletId())) {
            if (Objects.requireNonNull(action.getActionType()) == ActionType.CONSUME) {
                if (reservation.getPendingSettle() < action.getAmount()) {
                    // TODO maybe throw exception? this is severe error (overspend risk in reservation)
                    return Result.failure(WalletServiceErrorCode.WALLET_RESERVATION_NOT_SATISFIED, String.format("fail to consume, pending settle not enough, reservation: %s, action: %s ", reservation, action));
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
}
