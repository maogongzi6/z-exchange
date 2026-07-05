package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;

public class RetriableException extends CustomizedException {
    public RetriableException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
