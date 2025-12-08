package com.exchange.app.ledger.utils;

import com.exchange.app.ledger.exception.CustomException;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.exception.ServerError;

public class ErrorHelper {
    static public ErrorCode getErrorCode(Exception exception) {
        if (exception instanceof CustomException) {
            return ((CustomException) exception).getCode();
//        } else if (exception instanceof ProtobufBeanHelper.BadRequestException) {
//            return ErrorCode.BAD_REQUEST;
//        } else if (exception instanceof ProtobufBeanHelper.BadReplyException) {
//            return ErrorCode.BAD_REPLY;
        } else if (exception instanceof ServerError) {
            return ErrorCode.SERVER_ERROR;
        } else {
            return ErrorCode.UNKNOWN_ERROR;
        }
    }
}
