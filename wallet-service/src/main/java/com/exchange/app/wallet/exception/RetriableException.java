package com.exchange.app.wallet.exception;


import com.exchange.app.wallet.result.ErrorCode;

public class RetriableException extends CustomException {
    public RetriableException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
