package com.exchange.common.result;

import com.exchange.common.result.error.ErrorCode;

public record Result<T>(boolean success, T value, ErrorCode errorCode, String detail,
                        String scope) implements IResult<T> {
}
