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

    private LedgerMetrics() {
    }
}
