package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IdempErrorCode implements ErrorCode {
    INVALID_IDEMP_KEY(-1000, ErrorCategory.INTERNAL, "invalid_idemp_key"),
    INVALID_IDEMP_VALUE(-1001, ErrorCategory.INTERNAL, "invalid_idemp_value");

    private final int code;
    private final ErrorCategory category;
    private final String message;

    @Override
    public String getNamespace() {
        return ErrorNamespace.IDEMP;
    }
}
