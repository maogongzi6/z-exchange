package com.exchange.app.ledger.metrics;

import com.exchange.common.metrics.MetricDef;

public final class LedgerMetrics {
    public static final MetricDef LEDGER_REQUESTS = new MetricDef(
            "zexchange.ledger.requests",
            "Ledger business request result count"
    );

    public static final MetricDef LEDGER_PROCESSING_DURATION = new MetricDef(
            "zexchange.ledger.processing.duration",
            "Ledger business processing duration"
    );

    public static final MetricDef LEDGER_TRANSACTIONS_CREATED = new MetricDef(
            "zexchange.ledger.transactions.created",
            "Newly committed ledger transaction count"
    );

    public static final MetricDef LEDGER_TRANSACTIONS_IDEMPOTENT = new MetricDef(
            "zexchange.ledger.transactions.idempotent",
            "Successful idempotent ledger replay count"
    );

    public static final MetricDef LEDGER_ENTRIES_PER_TRANSACTION = new MetricDef(
            "zexchange.ledger.entries.per_transaction",
            "Number of entries in each newly committed ledger transaction"
    );

    public static final MetricDef LEDGER_IDEMPOTENCY_ERRORS = new MetricDef(
            "zexchange.ledger.idempotency.errors",
            "Idempotency-specific failure or abnormal state count"
    );

    public static final MetricDef CACHE_OPS = new MetricDef(
            "zexchange.cache.ops",
            "Cache read result count"
    );

    public static final MetricDef CACHE_ERRORS = new MetricDef(
            "zexchange.cache.errors",
            "Cache-specific error count"
    );

    public static final MetricDef REDIS_OPERATION_DURATION = new MetricDef(
            "zexchange.redis.operation.duration",
            "Client-observed Redis logical operation duration"
    );

    public static final MetricDef KAFKA_LISTENER_ACTIVE = new MetricDef(
            "zexchange.kafka.listener.active",
            "Current Kafka records being processed by the listener"
    );

    public static final MetricDef KAFKA_LISTENER_DURATION = new MetricDef(
            "zexchange.kafka.listener.duration",
            "Kafka listener processing duration"
    );

    public static final MetricDef KAFKA_PUBLISH_DURATION = new MetricDef(
            "zexchange.kafka.publish.duration",
            "Application-observed Kafka publish duration"
    );

    public static final MetricDef OUTBOX_LIFECYCLE = new MetricDef(
            "zexchange.outbox.lifecycle",
            "Successful high-level outbox lifecycle transition count"
    );

    public static final MetricDef OUTBOX_FAILURES = new MetricDef(
            "zexchange.outbox.failures",
            "Outbox failure count by lifecycle stage"
    );

    public static final MetricDef OUTBOX_PENDING = new MetricDef(
            "zexchange.outbox.pending",
            "Current pending outbox row count"
    );

    public static final MetricDef OUTBOX_DEAD = new MetricDef(
            "zexchange.outbox.dead",
            "Current dead outbox row count"
    );

    public static final MetricDef OUTBOX_PENDING_EXHAUSTED = new MetricDef(
            "zexchange.outbox.pending.exhausted",
            "Current pending outbox row count whose attempt count reached max retries"
    );

    public static final MetricDef OUTBOX_PENDING_OLDEST_AGE = new MetricDef(
            "zexchange.outbox.pending.oldest.age",
            "Age in seconds of the oldest pending outbox row"
    );

    public static final MetricDef OUTBOX_DISPATCH_DURATION = new MetricDef(
            "zexchange.outbox.dispatch.duration",
            "Scheduled outbox retry dispatch duration"
    );

    public static final MetricDef OUTBOX_DISPATCH_RECORDS = new MetricDef(
            "zexchange.outbox.dispatch.records",
            "Outbox records processed in each scheduled dispatch run"
    );

    public static final MetricDef DB_OPERATION_DURATION = new MetricDef(
            "zexchange.db.operation.duration",
            "Client-observed duration for a bounded database operation"
    );

    public static final MetricDef DB_TRANSACTION_DURATION = new MetricDef(
            "zexchange.db.transaction.duration",
            "Client-observed duration for a bounded database transaction block"
    );

    private LedgerMetrics() {
    }
}
