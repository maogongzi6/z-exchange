package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DbErrorCode implements RetryableErrorCode {
    DB_INTERNAL(-1300, ErrorCategory.INTERNAL, "db_internal", false),
    DB_TIMEOUT(-1301, ErrorCategory.UNAVAILABLE, "db_timeout", true),
    DB_UNAVAILABLE(-1302, ErrorCategory.UNAVAILABLE, "db_unavailable", true),
    DB_TRANSIENT(-1303, ErrorCategory.UNAVAILABLE, "db_transient", true);

    private final int code;
    private final ErrorCategory category;
    private final String message;
    private final boolean retryable;

    @Override
    public String getNamespace() {
        return ErrorNamespace.DB;
    }
}
