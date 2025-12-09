package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.ErrorCode;

public class DbException extends CustomException {
    public DbException(Throwable cause) {
        super(ErrorCode.DB_ERROR, cause);
    }
}
