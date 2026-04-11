package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.WalletServiceErrorCode;

public class DbException extends CustomException {
    public DbException(Throwable cause) {
        super(WalletServiceErrorCode.DB_ERROR, cause);
    }

    public DbException(String message) {
        super(WalletServiceErrorCode.DB_ERROR, message);
    }
}
