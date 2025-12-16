package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;

public class TransactionHelper {
    static public boolean txnHasFinalized(TransactionStatus txnStatus) {
        return txnStatus == TransactionStatus.COMPLETED || txnStatus == TransactionStatus.CLOSED;
    }
}
