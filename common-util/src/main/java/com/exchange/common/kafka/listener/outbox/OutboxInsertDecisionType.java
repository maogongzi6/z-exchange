package com.exchange.common.kafka.listener.outbox;

public enum OutboxInsertDecisionType {
    INSERTED,
    EXISTING_SAME_INTENT,
    EXISTING_MISSING_AMBIGUOUS,
    EXISTING_CONFLICT
}
