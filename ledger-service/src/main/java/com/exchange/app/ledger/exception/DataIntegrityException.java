package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.LedgerServiceErrorCode;

public class DataIntegrityException extends CustomException {
    public DataIntegrityException(Throwable cause) {
        super(LedgerServiceErrorCode.DB_ERROR, cause);
    }
}
