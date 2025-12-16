package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.ErrorCode;

public class InvalidValueException extends CustomException {
    public InvalidValueException(String message) {
        super(ErrorCode.INVALID_VALUE_ERROR, message);
    }
}
