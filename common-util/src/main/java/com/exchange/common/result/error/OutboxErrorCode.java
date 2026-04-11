package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxErrorCode implements ErrorCode {
    UNEXPECTED_DB_ERROR(-1, ErrorCategory.INTERNAL, "unexpected_db_error");

    private final int code;
    private final ErrorCategory category;
    private final String message;

    @Override
    public String getNamespace() {
        return ErrorNamespace.OUTBOX;
    }
}
