package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;

public class InvalidValueException extends CustomizedException {
    public InvalidValueException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
