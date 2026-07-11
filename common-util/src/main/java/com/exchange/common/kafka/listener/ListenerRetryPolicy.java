package com.exchange.common.kafka.listener;

public record ListenerRetryPolicy(
        long baseAttemptIntervalMs,
        int replyFailureAfterAttempts,
        int dlqAfterAttempts
) {
    public ListenerRetryPolicy {
        if (baseAttemptIntervalMs < 0) {
            throw new IllegalArgumentException("baseAttemptIntervalMs must be >= 0");
        }
        if (replyFailureAfterAttempts < 1) {
            throw new IllegalArgumentException("replyFailureAfterAttempts must be >= 1");
        }
        if (dlqAfterAttempts < replyFailureAfterAttempts) {
            throw new IllegalArgumentException("dlqAfterAttempts must be >= replyFailureAfterAttempts");
        }
    }
}
