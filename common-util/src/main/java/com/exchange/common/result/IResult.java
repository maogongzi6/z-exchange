package com.exchange.common.result;

import com.exchange.common.result.error.ErrorCode;

public interface IResult<T> {
    boolean isSuccess();
    ErrorCode getErrorCode();
    T getValue();
    String getDetail();
    String getScope();


    default boolean isFailed() {
        return !isSuccess();
    }
}
