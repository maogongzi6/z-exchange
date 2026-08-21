package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KafkaProducerErrorCode implements RetryableErrorCode {
    PUBLISH_FAILED(-1600, ErrorCategory.UNAVAILABLE, "publish_failed", true);

    private final int code;
    private final ErrorCategory category;
    private final String message;
    private final boolean retryable;

    @Override
    public String getNamespace() {
        return ErrorNamespace.KAFKA;
    }
}
