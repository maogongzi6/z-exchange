package com.exchange.common.kafka.listener.handler;

import com.exchange.common.kafka.listener.action.ListenerAction;
import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.proto.common.event.EventEnvelopePb;

import java.util.Objects;
import java.util.function.Function;

/*
 * Adapter for request/reply command listeners. Normal handling returns a reply outbox, and
 * parseable failures are converted into failed reply outboxes through the service-owned
 * FailureReplyFactory.
 */
public class OutboxReplyDefaultMessageHandler implements MessageHandler {
    private final Function<EventEnvelopePb, Outbox> handler;
    private final FailureReplyFactory failureReplyFactory;

    public OutboxReplyDefaultMessageHandler(
            Function<EventEnvelopePb, Outbox> handler,
            FailureReplyFactory failureReplyFactory
    ) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.failureReplyFactory = Objects.requireNonNull(failureReplyFactory, "failureReplyFactory");
    }

    @Override
    public ListenerAction handle(EventEnvelopePb envelope) {
        Outbox outbox = handler.apply(Objects.requireNonNull(envelope, "envelope"));
        return ListenerAction.reply(outbox);
    }

    @Override
    public ListenerAction handleFailure(EventEnvelopePb envelope, ListenerFailure failure) {
        Outbox outbox = failureReplyFactory.createFailureReply(
                Objects.requireNonNull(envelope, "envelope"),
                Objects.requireNonNull(failure, "failure")
        );
        return ListenerAction.reply(outbox);
    }
}
