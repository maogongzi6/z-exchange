package com.exchange.common.kafka.listener.exception;

import com.exchange.common.exception.CustomizedException;
import com.exchange.common.result.error.ErrorCode;

public class DlqException extends CustomizedException {
    public DlqException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DlqException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
