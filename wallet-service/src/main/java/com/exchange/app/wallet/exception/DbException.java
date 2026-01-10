package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.ErrorCode;

public class DbException extends CustomException {
    public DbException(Throwable cause) {
        super(ErrorCode.DB_ERROR, cause);
    }

    public DbException(String message) {
        super(ErrorCode.DB_ERROR, message);
    }
}
