package com.exchange.common.kafka.listener.retry;

import org.apache.kafka.common.header.Headers;

@FunctionalInterface
public interface ListenerAttemptResolver {
    int resolve(Headers headers);
}
