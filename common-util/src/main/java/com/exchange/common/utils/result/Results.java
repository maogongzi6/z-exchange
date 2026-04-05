package com.exchange.common.utils.result;

import java.util.Objects;

public class Results {
    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, CommonErrorCode.success(), null);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(PbMappableErrorCode error, String errorDetail) {
        return new Result<>(false, null, error, errorDetail);
    }

    public static <T> Result<T> result(T newValue, Result<?> result) {
        Objects.requireNonNull(result);
        return new Result<>(result.success(), newValue, result.errorCode(), result.errorDetail());
    }

    public static boolean is(Result<?> result, PbMappableErrorCode code) {
        return result != null && code != null && result.errorCode().getClass() == code.getClass() && result.errorCode() == code;
    }
}
