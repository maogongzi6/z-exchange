package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;
import lombok.Getter;

public class CustomizedException extends RuntimeException {
    @Getter
    private final ErrorCode errorCode;

    public CustomizedException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CustomizedException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
