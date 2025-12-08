package com.exchange.app.ledger.exception;

import com.exchange.app.ledger.result.ErrorCode;

public class CommonException extends CustomException {
    private CommonException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

//    static public CommonException badRequest(String message) {
//        return new CommonException(ErrorCode.BAD_REQUEST, message);
//    }

    static public CommonException invalidRequestParameter(String message) {
        return new CommonException(ErrorCode.INVALID_REQUEST_PARAMETERS, message);
    }
}