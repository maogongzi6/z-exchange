package com.example.exception;

public abstract class CustomException extends CustomThrowable {
    public CustomException(ErrorCode errorCode) {
        super(errorCode);
    }
    public CustomException(ErrorCode errorCode, String message) {super(errorCode, message);}
}
