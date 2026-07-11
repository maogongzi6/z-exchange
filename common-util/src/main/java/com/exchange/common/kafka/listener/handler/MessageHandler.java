package com.exchange.common.kafka.listener.handler;

import com.exchange.common.kafka.listener.action.ListenerAction;
import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.proto.common.event.EventEnvelopePb;

public interface MessageHandler {
    // TODO comment explain
    ListenerAction handle(EventEnvelopePb envelope);

    // TODO comment to explain: all exceptions besides KafkaListenerRetriableException send msg the dlq (verify whether this is true when commenting)
    ListenerAction handleFailure(EventEnvelopePb envelope, ListenerFailure failure);
}
