package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.transaction.ActionType;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;

import java.util.Objects;

public class WalletTxnHelper {
//    static public boolean isConsumeTransferOutPair(WalletAction consume, WalletAction transferOut) {
//        return consume != null && transferOut != null
//                && consume.getActionType() == ActionType.CONSUME
//                && transferOut.getActionType() == ActionType.TRANSFER_OUT
//                && Objects.equals(consume.getAmount(), transferOut.getAmount())
//                && Objects.equals(consume.getAssetId(), transferOut.getAssetId())
//                && Objects.equals(consume.getWalletId(), transferOut.getWalletId());
//    }

    static public boolean snapshotReservationMatched(BalanceSnapshot snapshot, WalletReservation reservation) {
        if (snapshot == null || reservation == null) {
            return false;
        }
        return Objects.equals(reservation.getWalletId(), snapshot.getWalletId()) && Objects.equals(reservation.getAssetId(), snapshot.getAssetId());
    }

    static public boolean actionSnapshotMatched(BalanceSnapshot snapshot, WalletAction action) {
        if (snapshot == null || action == null) {
            return false;
        }
        return Objects.equals(action.getWalletId(), snapshot.getWalletId()) && Objects.equals(action.getAssetId(), snapshot.getAssetId());
    }

    static public boolean actionReservationMatched(WalletAction action, WalletReservation reservation) {
        if (action == null || reservation == null) {
            return false;
        }
        return Objects.equals(action.getWalletId(), reservation.getWalletId()) && Objects.equals(action.getAssetId(), reservation.getAssetId());
    }
}
