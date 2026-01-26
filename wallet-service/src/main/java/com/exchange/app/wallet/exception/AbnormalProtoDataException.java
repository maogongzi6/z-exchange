package com.exchange.app.wallet.exception;

import com.exchange.app.wallet.result.ErrorCode;

public class AbnormalProtoDataException extends CustomException {
    public AbnormalProtoDataException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public AbnormalProtoDataException(ErrorCode code, String message) {
        super(code, message);
    }
}
