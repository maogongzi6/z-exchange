package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;

public class CustomizedException extends CustomThrowable {
    public CustomizedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CustomizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CustomizedException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public CustomizedException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
