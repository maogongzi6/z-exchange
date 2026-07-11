package com.exchange.common.kafka.listener.outbox;

import com.exchange.common.outbox.po.Outbox;

public record OutboxInsertDecision(
        OutboxInsertDecisionType type,
        Outbox intended,
        Outbox existing
) {
    public static OutboxInsertDecision inserted(Outbox intended) {
        return new OutboxInsertDecision(OutboxInsertDecisionType.INSERTED, intended, null);
    }

    public static OutboxInsertDecision existingSameIntent(Outbox intended, Outbox existing) {
        return new OutboxInsertDecision(OutboxInsertDecisionType.EXISTING_SAME_INTENT, intended, existing);
    }

    public static OutboxInsertDecision existingMissingAmbiguous(Outbox intended) {
        return new OutboxInsertDecision(OutboxInsertDecisionType.EXISTING_MISSING_AMBIGUOUS, intended, null);
    }

    public static OutboxInsertDecision existingConflict(Outbox intended, Outbox existing) {
        return new OutboxInsertDecision(OutboxInsertDecisionType.EXISTING_CONFLICT, intended, existing);
    }
}
