package com.exchange.common.kafka.listener.failure;

public enum ListenerFailureType {
    NON_RETRYABLE,
    UNEXPECTED,
    RETRY_EXHAUSTED
}
