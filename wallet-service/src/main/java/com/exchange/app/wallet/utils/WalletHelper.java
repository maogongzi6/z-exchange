package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.WalletStatus;

public class WalletHelper {
    static public boolean hasInitiated(WalletStatus walletStatus) {
        return walletStatus != WalletStatus.UNKNOWN && walletStatus != WalletStatus.INIT;
    }
}
