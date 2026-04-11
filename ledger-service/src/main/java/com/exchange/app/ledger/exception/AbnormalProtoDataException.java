package com.exchange.app.ledger.exception;

import com.exchange.common.result.error.ErrorCode;

public class AbnormalProtoDataException extends CustomException {
    public AbnormalProtoDataException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public AbnormalProtoDataException(ErrorCode code, String message) {
        super(code, message);
    }
}
