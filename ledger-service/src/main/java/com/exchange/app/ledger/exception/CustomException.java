package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.ErrorCode;

public abstract class CustomException extends CustomThrowable {
    public CustomException(ErrorCode errorCode) {
        super(errorCode);
    }
    public CustomException(ErrorCode errorCode, String message) {super(errorCode, message);}
    public CustomException(ErrorCode errorCode, Throwable cause) {super(errorCode, cause);}
    public CustomException(ErrorCode errorCode, String message, Throwable cause) {super(errorCode, message, cause);}
}
