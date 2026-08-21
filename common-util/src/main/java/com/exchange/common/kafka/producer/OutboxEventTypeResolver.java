package com.exchange.common.kafka.producer;

import org.springframework.lang.Nullable;

@FunctionalInterface
public interface OutboxEventTypeResolver {
    /**
     * @return the event type name, or {@code null} when this resolver does not support the event type
     */
    @Nullable
    String resolve(int eventType);
}
