package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;

public class CacheException extends CustomizedException {
    public CacheException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public CacheException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
