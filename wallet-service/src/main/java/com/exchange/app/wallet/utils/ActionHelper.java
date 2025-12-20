package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.transaction.ActionType;
import com.exchange.app.wallet.po.transaction.WalletAction;

import java.util.Objects;

public class ActionHelper {
    static public boolean isConsumeTransferOutPair(WalletAction consume, WalletAction transferOut) {
        return consume != null && transferOut != null
                && consume.getActionType() == ActionType.CONSUME
                && transferOut.getActionType() == ActionType.TRANSFER_OUT
                && Objects.equals(consume.getAmount(), transferOut.getAmount())
                && Objects.equals(consume.getAssetId(), transferOut.getAssetId())
                && Objects.equals(consume.getWalletId(), transferOut.getWalletId());
    }
}
