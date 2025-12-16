package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;

public class WalletHelper {
    static public boolean walletHasInitiated(WalletStatus walletStatus) {
        return walletStatus != WalletStatus.UNKNOWN && walletStatus != WalletStatus.INIT;
    }
}
