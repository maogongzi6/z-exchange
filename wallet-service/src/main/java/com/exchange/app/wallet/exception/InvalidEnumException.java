package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.ErrorCode;

public class InvalidEnumException extends CustomException {
    public InvalidEnumException(String message) {
        super(ErrorCode.INVALID_ENUM_ERROR, message);
    }
}
