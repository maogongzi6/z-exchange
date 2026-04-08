package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CacheErrorCode implements ErrorCode {
    PARSE_CACHE_ERROR(-1100, ErrorCategory.INTERNAL, "parse_cache_error");

    private final int code;
    private final ErrorCategory category;
    private final String message;

    @Override
    public String getNamespace() {
        return ErrorNamespace.CACHE;
    }
}
