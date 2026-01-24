package com.exchange.app.ledger.result;

import com.exchange.common.utils.result.CommonResult;

// TODO maybe move success() fail() to common module
public class Result<T> extends CommonResult<T, ErrorCode> {

    private Result(boolean success, T value, ErrorCode errorCode, String errorDetail) {
        super(success, value, errorCode, errorDetail);
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, ErrorCode.success(), null);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ErrorCode error, String errorDetail) {
        return new Result<>(false, null, error, errorDetail);
    }

    public static <T> Result<T> fail(Result<?> result) {
        return fail(result.errorCode, result.errorDetail);
    }

    public static <T> Result<T> requireNotNull(Result<T> result) {
        return (Result<T>) requireNotNull(result, ErrorCode.NULL_RESULT);
    }

    public static <T, R> Result<T> result(T newValue, Result<R> result) {
        result = requireNotNull(result);
        return new Result<>(result.success, newValue, result.errorCode, result.errorDetail);
    }

}
