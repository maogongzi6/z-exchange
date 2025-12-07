package com.example.utils;

import com.example.po.enums.WalletStatus;

public class WalletHelper {
    static public boolean hasInitiated(WalletStatus walletStatus) {
        return walletStatus != WalletStatus.UNKNOWN && walletStatus != WalletStatus.INIT;
    }
}
