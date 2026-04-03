package com.exchange.common.utils.result;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.lang.NonNull;

@ToString
@RequiredArgsConstructor
public class Result<T> {
    public final boolean success;
    public final T value;

    // TODO add SCOPE here

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
}