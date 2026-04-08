package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IdempErrorCode implements ErrorCode {
    // TODO
    ;

    private final int code;
    private final ErrorCategory category;
    private final String message;

    @Override
    public String getNamespace() {
        return ErrorNamespace.IDEMP;
    }
}
