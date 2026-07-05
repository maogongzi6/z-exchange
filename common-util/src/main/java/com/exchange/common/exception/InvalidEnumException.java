package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;

public class InvalidEnumException extends CustomizedException {
    public InvalidEnumException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
