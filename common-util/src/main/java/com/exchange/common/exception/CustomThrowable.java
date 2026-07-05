package com.exchange.common.exception;

import com.exchange.common.result.error.ErrorCode;
import lombok.Getter;

@Getter
public abstract class CustomThrowable extends RuntimeException {
    private final ErrorCode errorCode;

    public CustomThrowable(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomThrowable(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CustomThrowable(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public CustomThrowable(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getCode() {
        return errorCode;
    }
}
