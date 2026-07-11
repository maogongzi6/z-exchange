package com.exchange.common.kafka.listener.exception;

import com.exchange.common.exception.CustomizedException;
import com.exchange.common.result.error.ErrorCode;

public class KafkaListenerRetriableException extends CustomizedException {
    public KafkaListenerRetriableException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public KafkaListenerRetriableException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
