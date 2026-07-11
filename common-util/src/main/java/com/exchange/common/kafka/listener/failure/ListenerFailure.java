package com.exchange.common.kafka.listener.failure;

import com.exchange.common.result.error.ErrorCode;

import java.util.Objects;

public record ListenerFailure(
        ErrorCode errorCode,
        String detail,
        Throwable cause,
        ListenerFailureType type
) {
    public ListenerFailure {
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(type, "type");
        detail = detail == null || detail.isBlank() ? errorCode.getMessage() : detail;
    }

    public static ListenerFailure nonRetryable(ErrorCode errorCode, String detail, Throwable cause) {
        return new ListenerFailure(errorCode, detail, cause, ListenerFailureType.NON_RETRYABLE);
    }

    public static ListenerFailure nonRetryable(ErrorCode errorCode, String detail) {
        return nonRetryable(errorCode, detail, null);
    }

    public static ListenerFailure unexpected(ErrorCode errorCode, String detail, Throwable cause) {
        return new ListenerFailure(errorCode, detail, cause, ListenerFailureType.UNEXPECTED);
    }

    public static ListenerFailure unexpected(ErrorCode errorCode, String detail) {
        return unexpected(errorCode, detail, null);
    }

    public static ListenerFailure retryExhausted(ErrorCode errorCode, String detail, Throwable cause) {
        return new ListenerFailure(errorCode, detail, cause, ListenerFailureType.RETRY_EXHAUSTED);
    }

    public static ListenerFailure retryExhausted(ErrorCode errorCode, String detail) {
        return retryExhausted(errorCode, detail, null);
    }
}
