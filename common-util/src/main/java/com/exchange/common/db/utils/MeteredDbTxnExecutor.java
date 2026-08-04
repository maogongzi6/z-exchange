package com.exchange.common.db.utils;

import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.result.Result;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.function.Supplier;

public class MeteredDbTxnExecutor implements DbTxnExecutor {
    private final DbTxnExecutor delegate;
    private final MeterRegistry meterRegistry;
    private final Timer successTimer;
    private final Timer errorTimer;

    public MeteredDbTxnExecutor(DbTxnExecutor delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        // Transactions are a hot path. Cache both bounded timer variants so recording does not
        // repeat builder/register lookups against Micrometer's meter registry on every execution.
        this.successTimer = transactionTimer(CommonMetricTagValues.Outcomes.SUCCESS);
        this.errorTimer = transactionTimer(CommonMetricTagValues.Outcomes.ERROR);
    }

    @Override
    public <T extends Result<?>> T executeWithDefault(Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        // A failed Result, callback exception, or commit failure all remain error outcomes.
        Timer outcomeTimer = errorTimer;
        try {
            T result = delegate.executeWithDefault(supplier);
            if (result != null && result.isSuccess()) {
                outcomeTimer = successTimer;
            }
            return result;
        } finally {
            // The delegate returns only after commit/rollback, so the timer covers the full transaction.
            sample.stop(outcomeTimer);
        }
    }

    private Timer transactionTimer(String outcome) {
        return Timer.builder(CommonMetrics.DB_TRANSACTION_DURATION.name())
                .description(CommonMetrics.DB_TRANSACTION_DURATION.description())
                .tag(CommonMetricTags.OUTCOME, outcome)
                .register(meterRegistry);
    }
}
