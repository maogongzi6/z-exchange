package com.exchange.common.kafka.listener.handler;

import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.proto.common.event.EventEnvelopePb;

@FunctionalInterface
public interface FailureReplyFactory {
    Outbox createFailureReply(EventEnvelopePb envelope, ListenerFailure failure);
}
