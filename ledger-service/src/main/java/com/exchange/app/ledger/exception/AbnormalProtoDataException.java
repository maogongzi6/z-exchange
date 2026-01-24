package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.ErrorCode;

public class AbnormalProtoDataException extends CustomException {
    public AbnormalProtoDataException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public AbnormalProtoDataException(ErrorCode code, String message) {
        super(code, message);
    }
}
