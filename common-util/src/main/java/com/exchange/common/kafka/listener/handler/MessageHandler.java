package com.exchange.common.kafka.listener.handler;

import com.exchange.common.kafka.listener.action.ListenerAction;
import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.proto.common.event.EventEnvelopePb;

public interface MessageHandler {
    // Build the normal listener action after the envelope has been parsed.
    ListenerAction handle(EventEnvelopePb envelope);

    /*
     * Convert parseable non-retryable or unexpected failures into the listener action that
     * represents this service's failure policy. For reply-required commands, this usually
     * means a failed reply outbox; for ack-only listeners it may be an explicit ack/DLQ
     * policy chosen by the service.
     */
    ListenerAction handleFailure(EventEnvelopePb envelope, ListenerFailure failure);
}
