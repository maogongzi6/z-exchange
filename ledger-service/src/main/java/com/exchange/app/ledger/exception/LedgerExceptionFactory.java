package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.common.exception.InvalidValueException;

public final class LedgerExceptionFactory {
    private LedgerExceptionFactory() {
    }

    public static InvalidValueException invalidValue(String message) {
        return new InvalidValueException(LedgerServiceErrorCode.INVALID_VALUE_ERROR, message);
    }
}
