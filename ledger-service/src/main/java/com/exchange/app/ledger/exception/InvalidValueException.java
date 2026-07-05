package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.LedgerServiceErrorCode;

public class InvalidValueException extends CustomException {
    public InvalidValueException(String message) {
        super(LedgerServiceErrorCode.INVALID_REQUEST_PARAMETER, message);
    }
}
