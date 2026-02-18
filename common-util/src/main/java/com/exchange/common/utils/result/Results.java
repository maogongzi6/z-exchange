package com.exchange.common.utils.result;

public class Results {
    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, CommonErrorCode.success(), null);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(CommonErrorCode error, String errorDetail) {
        return new Result<>(false, null, error, errorDetail);
    }
}
