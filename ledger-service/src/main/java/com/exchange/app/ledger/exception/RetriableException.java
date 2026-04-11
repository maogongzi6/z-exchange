package com.exchange.app.ledger.exception;

import com.exchange.common.result.error.ErrorCode;

public class RetriableException extends CustomException {
    public RetriableException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
