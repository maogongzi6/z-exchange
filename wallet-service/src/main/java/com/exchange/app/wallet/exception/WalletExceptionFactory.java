package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.exception.InvalidEnumException;
import com.exchange.common.exception.InvalidValueException;

public final class WalletExceptionFactory {
    private WalletExceptionFactory() {
    }

    public static InvalidValueException invalidValue(String message) {
        return new InvalidValueException(WalletServiceErrorCode.INVALID_VALUE_ERROR, message);
    }

    public static InvalidEnumException invalidEnum(String message) {
        return new InvalidEnumException(WalletServiceErrorCode.INVALID_ENUM_ERROR, message);
    }
}
