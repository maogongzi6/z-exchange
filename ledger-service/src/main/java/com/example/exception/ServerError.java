package com.example.exception;

public class ServerError extends CustomThrowable {
    private ServerError(ErrorCode code) {
        super(code);
    }

}
