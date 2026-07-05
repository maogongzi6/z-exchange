package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;

public class AbnormalProtoDataException extends CustomizedException {
    public AbnormalProtoDataException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AbnormalProtoDataException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
