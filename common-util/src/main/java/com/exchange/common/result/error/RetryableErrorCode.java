package com.exchange.common.result.error;

public interface RetryableErrorCode extends ErrorCode {
    boolean isRetryable();
}
