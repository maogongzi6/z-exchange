package com.example.exception;

public class ServerError extends CustomThrowable {
    private ServerError(ErrorCode code) {
        super(code);
    }
    private ServerError(ErrorCode code, String message) {
        super(code, message);
    }

    static public ServerError invalidDbParameter(String message) {
        return new ServerError(ErrorCode.INVALID_DB_PARAMETERS, message);
    }
}
