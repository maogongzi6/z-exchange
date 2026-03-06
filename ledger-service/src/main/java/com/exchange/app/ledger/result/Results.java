package com.exchange.app.ledger.result;


import com.exchange.common.utils.result.PbMappableErrorCode;
import com.exchange.common.utils.result.Result;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public class Results {
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
        return fail(getErrorCode(result), result.errorDetail);
    }

    public static <T> Result<T> result(T newValue, Result<T> result) {
        Objects.requireNonNull(result);
        return new Result<>(result.success, newValue, getErrorCode(result), result.errorDetail);
    }

    public static boolean is(Result<?> result, PbMappableErrorCode errorCode) {
        return result != null && result.errorCode.equals(errorCode);
    }

    public static ErrorCode getErrorCode(Result<?> result) {
        if (result.errorCode.getClass() != ErrorCode.class) {
            log.error("invalid result code class {}, result: {}", result.errorCode.getClass(), result);
            throw new ClassCastException(result.errorCode.getClass().toString());
        }
        return (ErrorCode) result.errorCode;
    }
}
