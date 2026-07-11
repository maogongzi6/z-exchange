package com.exchange.common.kafka.listener.handler;

import com.exchange.common.kafka.listener.action.ListenerAction;
import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.proto.common.event.EventEnvelopePb;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/*
 * Adapter for listeners whose durable work is completed inside the consumer function and
 * no reply outbox is required. Failure handling is injected so services must choose their
 * own policy instead of common-util silently acknowledging every failure.
 */
public class AckOnlyDefaultMessageHandler implements MessageHandler {
    private final Consumer<EventEnvelopePb> consumer;
    private final BiFunction<EventEnvelopePb, ListenerFailure, ListenerAction> failurePolicy;

    public AckOnlyDefaultMessageHandler(Consumer<EventEnvelopePb> consumer) {
        this(consumer, (envelope, failure) -> ListenerAction.ack());
    }

    public AckOnlyDefaultMessageHandler(
            Consumer<EventEnvelopePb> consumer,
            BiFunction<EventEnvelopePb, ListenerFailure, ListenerAction> failurePolicy
    ) {
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
    }

    @Override
    public ListenerAction handle(EventEnvelopePb envelope) {
        consumer.accept(Objects.requireNonNull(envelope, "envelope"));
        return ListenerAction.ack();
    }

    @Override
    public ListenerAction handleFailure(EventEnvelopePb envelope, ListenerFailure failure) {
        ListenerAction action = failurePolicy.apply(
                Objects.requireNonNull(envelope, "envelope"),
                Objects.requireNonNull(failure, "failure")
        );
        return Objects.requireNonNull(action, "failurePolicy action");
    }
}
