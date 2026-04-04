package com.exchange.app.wallet.result;

import com.exchange.common.utils.result.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Results {
    public static <T> Result<T> success(T value, String detail) {
        return new Result<>(true, value, ErrorCode.success(), detail);
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
        return fail(getErrorCode(result), result.errorDetail());
    }

    public static <T> Result<T> result(T newValue, Result<T> result) {
        return new Result<>(result.success(), newValue, getErrorCode(result), result.errorDetail());
    }

    public static ErrorCode getErrorCode(Result<?> result) {
        if (result.errorCode().getClass() != ErrorCode.class) {
            log.error("invalid result code class {}, result: {}", result.errorCode().getClass(), result);
            throw new ClassCastException(result.errorCode().getClass().toString());
        }
        return (ErrorCode) result.errorCode();
    }
}
