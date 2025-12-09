package com.exchange.common.result;

public class CommonResult<T, E extends PbMappableErrorCode> {
    public final boolean success;
    public final T value;
    public final E errorCode;
    public final String errorDetail;

    public CommonResult(boolean success, T value, E errorCode, String errorDetail) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
    }

    public static <E extends PbMappableErrorCode> boolean is(CommonResult<?,E> result, E errorCode) {
        return result != null && result.errorCode == errorCode;
    }

    public static boolean isSuccess(CommonResult<?,?> result) {
        return result != null && result.success;
    }

    public static <T, E extends PbMappableErrorCode> CommonResult<T, E> requireNotNull(CommonResult<T, E> result, E resultNullError) {
        return result == null ? new CommonResult<>(false, null, resultNullError, "null_result") : result;
    }
}