package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.WalletServiceErrorCode;

public class InvalidValueException extends CustomException {
    public InvalidValueException(String message) {
        super(WalletServiceErrorCode.INVALID_VALUE_ERROR, message);
    }
}
