package com.exchange.common.kafka.listener.action;

import com.exchange.common.outbox.po.Outbox;

import java.util.Objects;

public sealed interface ListenerAction permits ListenerAction.AckOnly, ListenerAction.OutboxReply {
    static AckOnly ack() {
        return new AckOnly();
    }

    static OutboxReply reply(Outbox outbox) {
        return new OutboxReply(outbox);
    }

    record AckOnly() implements ListenerAction {
    }

    record OutboxReply(Outbox outbox) implements ListenerAction {
        public OutboxReply {
            Objects.requireNonNull(outbox, "outbox");
        }
    }
}
