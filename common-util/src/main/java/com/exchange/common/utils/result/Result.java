package com.exchange.common.utils.result;

import org.springframework.lang.NonNull;

public record Result<T>(boolean success, T value, @NonNull PbMappableErrorCode errorCode, String errorDetail) {
    // TODO add SCOPE here

    public boolean isFailed() {
        return !success;
    }

    public static boolean isResultFailed(Result<?> result) {
        return !result.success;
    }
}