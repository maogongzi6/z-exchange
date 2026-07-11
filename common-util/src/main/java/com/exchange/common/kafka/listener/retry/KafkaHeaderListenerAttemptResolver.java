package com.exchange.common.kafka.listener.retry;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class KafkaHeaderListenerAttemptResolver implements ListenerAttemptResolver {
    private static final int FIRST_ATTEMPT = 1;

    @Override
    public int resolve(Headers headers) {
        if (headers == null) {
            return FIRST_ATTEMPT;
        }

        int retryTopicAttempt = readPositiveAttempt(headers.lastHeader(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS));
        if (retryTopicAttempt > 0) {
            return retryTopicAttempt;
        }

        int deliveryAttempt = readPositiveAttempt(headers.lastHeader(KafkaHeaders.DELIVERY_ATTEMPT));
        if (deliveryAttempt > 0) {
            return deliveryAttempt;
        }

        return FIRST_ATTEMPT;
    }

    private int readPositiveAttempt(Header header) {
        if (header == null || header.value() == null || header.value().length == 0) {
            return 0;
        }

        Integer binaryAttempt = readBinaryAttempt(header.value());
        if (binaryAttempt != null && binaryAttempt > 0) {
            return binaryAttempt;
        }

        Integer textAttempt = readTextAttempt(header.value());
        if (textAttempt != null && textAttempt > 0) {
            return textAttempt;
        }

        return 0;
    }

    private Integer readBinaryAttempt(byte[] value) {
        if (value.length == Integer.BYTES) {
            return ByteBuffer.wrap(value).getInt();
        }
        if (value.length == Long.BYTES) {
            long attempt = ByteBuffer.wrap(value).getLong();
            return attempt > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) attempt;
        }
        return null;
    }

    private Integer readTextAttempt(byte[] value) {
        try {
            return Integer.parseInt(new String(value, StandardCharsets.UTF_8));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
