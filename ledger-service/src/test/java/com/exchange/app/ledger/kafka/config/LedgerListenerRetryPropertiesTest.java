package com.exchange.app.ledger.kafka.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerListenerRetryPropertiesTest {
    @Test
    void retryPropertiesRejectInvalidThreshold() {
        // KL-CFG-001: the failed-reply threshold cannot exceed RetryableTopic attempts.
        LedgerListenerRetryProperties properties = new LedgerListenerRetryProperties();
        properties.setAttempts(4);
        properties.setReplyFailureAfterAttempts(5);

        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void retryPropertiesAllowEqualThreshold() {
        // KL-CFG-002: equal values mean the final retry attempt tries to create a failed reply.
        LedgerListenerRetryProperties properties = new LedgerListenerRetryProperties();
        properties.setAttempts(5);
        properties.setReplyFailureAfterAttempts(5);

        assertDoesNotThrow(properties::validate);
    }
}
