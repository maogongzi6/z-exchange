package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.ErrorCode;

public class DataIntegrityException extends CustomException {
    public DataIntegrityException(Throwable cause) {
        super(ErrorCode.DB_ERROR, cause);
    }
}
