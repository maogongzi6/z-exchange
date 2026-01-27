package com.exchange.common.utils.result;

import lombok.AllArgsConstructor;
import lombok.ToString;
import org.springframework.lang.NonNull;

import java.util.Objects;

@ToString
@AllArgsConstructor
public class Result<T> {
    public final boolean success;
    public final T value;
    @NonNull
    public final PbMappableErrorCode errorCode;
    public final String errorDetail;

    public boolean isFailed() {
        return !success;
    }

    public static boolean is(Result<?> result, PbMappableErrorCode code) {
        return result != null && code != null && result.errorCode.getClass() == code.getClass() && result.errorCode == code;
    }

    public static boolean isResultFailed(Result<?> result) {
        return !result.success;
    }

//    public static <T> Result<T> requireNotNull(Result<T> result, PbMappableErrorCode resultNullError) {
//        return result == null ? new Result<>(false, null, resultNullError, "null_result") : result;
//    }
}