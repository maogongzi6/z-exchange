package com.exchange.app.wallet.processor.transaction.model;

import com.exchange.app.wallet.po.enums.transaction.ActionType;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// memory cache, not real time data
@RequiredArgsConstructor
public class TransactionInfo {
    public final WalletTransaction walletTxn;
    public final List<WalletAction> actions;
    public final Map<String, List<WalletAction>> walletIdToPostActions; // only CONSUME, TRANSFER_OUT, TRANSFER_IN
    public final List<WalletReservation> reservations;
    public final List<BalanceSnapshot> snapshots;

    public static TransactionInfo create(WalletTransaction txn, List<WalletAction> actions, List<WalletReservation> reservations, List<BalanceSnapshot> snapshots) {
        Map<String, List<WalletAction>> walletIdToActions = new HashMap<>();
        for (WalletAction action : actions) {
            if (!action.isTransfer() && action.getActionType() != ActionType.CONSUME) {
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