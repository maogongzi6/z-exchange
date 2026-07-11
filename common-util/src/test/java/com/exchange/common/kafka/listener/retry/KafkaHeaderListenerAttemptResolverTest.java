package com.exchange.common.kafka.listener.retry;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaHeaderListenerAttemptResolverTest {
    private final KafkaHeaderListenerAttemptResolver resolver = new KafkaHeaderListenerAttemptResolver();

    @Test
    void missingHeadersResolveToFirstAttempt() {
        // KL-AR-001: missing delivery metadata represents the first source-processing attempt.
        assertEquals(1, resolver.resolve(null));
        assertEquals(1, resolver.resolve(new RecordHeaders()));
    }

    @Test
    void retryTopicAttemptHeaderHasPriority() {
        // KL-AR-002: retry-topic attempts survive non-blocking retry hops and should win.
        Headers headers = new RecordHeaders()
                .add(KafkaHeaders.DELIVERY_ATTEMPT, intBytes(2))
                .add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, intBytes(5));

        assertEquals(5, resolver.resolve(headers));
    }

    @Test
    void binaryIntegerAttemptHeaderIsSupported() {
        // KL-AR-003: Spring may store attempts as binary integers.
        Headers headers = new RecordHeaders()
                .add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, intBytes(4));

        assertEquals(4, resolver.resolve(headers));
    }

    @Test
    void binaryLongAttemptHeaderIsSupportedAndCapped() {
        // KL-AR-004: long values are normalized to int and capped defensively.
        Headers normal = new RecordHeaders()
                .add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, longBytes(6L));
        Headers tooLarge = new RecordHeaders()
                .add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, longBytes(((long) Integer.MAX_VALUE) + 1L));

        assertEquals(6, resolver.resolve(normal));
        assertEquals(Integer.MAX_VALUE, resolver.resolve(tooLarge));
    }

    @Test
    void textAttemptHeaderIsSupported() {
        // KL-AR-005: text headers keep tests and manual diagnostics straightforward.
        Headers headers = new RecordHeaders()
                .add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, "5".getBytes(StandardCharsets.UTF_8));

        assertEquals(5, resolver.resolve(headers));
    }

    @Test
    void invalidOrNonPositiveRetryAttemptFallsBackToDeliveryAttemptOrFirstAttempt() {
        // KL-AR-006: malformed retry-topic header should not hide a valid delivery attempt.
        Headers withFallback = new RecordHeaders()
                .add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, "bad".getBytes(StandardCharsets.UTF_8))
                .add(KafkaHeaders.DELIVERY_ATTEMPT, intBytes(3));
        Headers invalidOnly = new RecordHeaders()
                .add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, intBytes(0))
                .add(KafkaHeaders.DELIVERY_ATTEMPT, intBytes(-1));

        assertEquals(3, resolver.resolve(withFallback));
        assertEquals(1, resolver.resolve(invalidOnly));
    }

    private byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}
