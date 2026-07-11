package com.exchange.common.result.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KafkaListenerErrorCode implements RetryableErrorCode {
    ENVELOPE_PARSE_ERROR(-1500, ErrorCategory.INVALID_ARGUMENT, "envelope_parse_error", false),
    RETRY_EXHAUSTED(-1501, ErrorCategory.UNAVAILABLE, "retry_exhausted", false),
    REPLY_OUTBOX_AMBIGUOUS(-1502, ErrorCategory.UNAVAILABLE, "reply_outbox_ambiguous", true),
    REPLY_OUTBOX_CONFLICT(-1503, ErrorCategory.CONFLICT, "reply_outbox_conflict", false),
    LISTENER_INTERNAL_ERROR(-1504, ErrorCategory.INTERNAL, "listener_internal_error", false),
    ACK_FAILED(-1505, ErrorCategory.UNAVAILABLE, "ack_failed", true);

    private final int code;
    private final ErrorCategory category;
    private final String message;
    private final boolean retryable;

    @Override
    public String getNamespace() {
        return ErrorNamespace.KAFKA;
    }
}
