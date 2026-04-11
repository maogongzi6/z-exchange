package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.WalletServiceErrorCode;

public class InvalidEnumException extends CustomException {
    public InvalidEnumException(String message) {
        super(WalletServiceErrorCode.INVALID_ENUM_ERROR, message);
    }
}
