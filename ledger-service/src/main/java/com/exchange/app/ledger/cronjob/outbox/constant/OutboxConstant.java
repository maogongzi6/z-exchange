package com.exchange.app.ledger.cronjob.outbox.constant;

import java.util.concurrent.TimeUnit;

public final class OutboxConstant {
    private OutboxConstant() {
    }

    public static final long DEFAULT_BASE_ATTEMPT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15);
}
