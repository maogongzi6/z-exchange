package com.exchange.common.result;

import com.exchange.common.result.error.ErrorCode;

public interface IResult<T> {
    boolean success();
    ErrorCode errorCode();
    T value();
    String detail();
    String scope();

    default boolean isFailed() {
        return !success();
    }
}
