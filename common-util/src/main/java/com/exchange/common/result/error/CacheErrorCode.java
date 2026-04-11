package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CacheErrorCode implements ErrorCode {
    CONNECTION(-1100, ErrorCategory.UNAVAILABLE, "connection"),
    TIMEOUT(-1101, ErrorCategory.UNAVAILABLE, "timeout"),
    ACCESS(-1102, ErrorCategory.INTERNAL, "access"),
    SERIALIZATION(-1103, ErrorCategory.INTERNAL, "serialization"),
    MALFORMED_VALUE(-1104, ErrorCategory.INTERNAL, "malformed_value"),
    SCRIPT(-1105, ErrorCategory.INTERNAL, "script"),
    CONFIGURATION(-1106, ErrorCategory.INTERNAL, "configuration"),
    UNEXPECTED_INTERNAL(-1107, ErrorCategory.INTERNAL, "unexpected_internal");

    private final int code;
    private final ErrorCategory category;
    private final String message;

    @Override
    public String getNamespace() {
        return ErrorNamespace.CACHE;
    }
}
