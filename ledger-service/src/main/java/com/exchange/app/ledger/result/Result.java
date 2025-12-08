package com.exchange.app.ledger.result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Result<T>{
    public final boolean success;
    public final T value;
    public final ErrorCode errorCode;
    public final String errorDetail;

    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, ErrorCode.success(), null);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ErrorCode error, String errorDetail) {
        return new Result<>(false, null, error, errorDetail);
    }
}
