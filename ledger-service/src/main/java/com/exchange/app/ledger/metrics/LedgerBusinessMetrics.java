package com.exchange.app.ledger.metrics;

import com.exchange.app.ledger.result.LedgerBoundaryErrorMapper;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.app.ledger.result.PostTransactionResult;
import com.exchange.common.metrics.MetricDef;
import com.exchange.common.result.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class LedgerBusinessMetrics {
    private final MeterRegistry registry;

    /**
     * Records the post-transaction business request and processing duration.
     * <p>
     * The processor returns transport-neutral business facts; this wrapper owns ingress tags,
     * derives success/error labels, and emits created/idempotent counters from the result kind.
     */
    public PostTransactionResult recordPostTransaction(String ingress, Supplier<PostTransactionResult> action) {
        return recordBusinessRequest(
                LedgerMetricTagValues.Operations.POST_TRANSACTION,
                ingress,
                action,
                result -> {
                    recordPostTransactionFacts(ingress, result);
                    return fromPostTransactionResult(result);
                }
        );
    }

    /**
     * Records a ledger transaction read request and processing duration.
     * <p>
     * The caller supplies the normalized lookup operation so query-by-id and query-by-ref
     * remain separate low-cardinality metric series.
     */
    public <T> Result<T> recordLedgerTxnQuery(String operation, String ingress, Supplier<Result<T>> action) {
        return recordBusinessRequest(
                operation,
                ingress,
                action,
                this::fromResult
        );
    }

    /**
     * Records facts that are true only for newly committed ledger transactions.
     * <p>
     * Idempotent replays deliberately do not increment created count or entry complexity.
     */
    private void recordLedgerTransactionCreated(int entryCount) {
        counter(LedgerMetrics.LEDGER_TRANSACTIONS_CREATED).increment();
        DistributionSummary.builder(LedgerMetrics.LEDGER_ENTRIES_PER_TRANSACTION.name())
                .description(LedgerMetrics.LEDGER_ENTRIES_PER_TRANSACTION.description())
                .baseUnit("entries")
                .register(registry)
                .record(entryCount);
    }

    /**
     * Records successful idempotent replays separately from newly created transactions.
     * <p>
     * Source distinguishes the fast Redis path from the DB backstop without tagging by request key.
     */
    private void recordIdempotentTransaction(String ingress, String source) {
        Counter.builder(LedgerMetrics.LEDGER_TRANSACTIONS_IDEMPOTENT.name())
                .description(LedgerMetrics.LEDGER_TRANSACTIONS_IDEMPOTENT.description())
                .tag(LedgerMetricTags.INGRESS, ingress)
                .tag(LedgerMetricTags.SOURCE, source)
                .register(registry)
                .increment();
    }

    /**
     * Shared timing wrapper for ledger business entrypoints.
     * <p>
     * It records request count and duration exactly once, including thrown exceptions as bounded
     * server errors, while preserving the original return value/exception behavior.
     */
    private <T> T recordBusinessRequest(String operation, String ingress, Supplier<T> action,
                                        Function<T, BusinessOutcome> outcomeExtractor) {
        Timer.Sample sample = Timer.start(registry);
        // If the action throws, still record a bounded business error instead of dropping the sample.
        BusinessOutcome outcome = BusinessOutcome.error(LedgerServiceErrorCode.SERVER_ERROR.getMessage());

        try {
            T value = action.get();
            outcome = outcomeExtractor.apply(value);
            return value;
        } finally {
            recordRequest(operation, ingress, outcome);
            sample.stop(Timer.builder(LedgerMetrics.LEDGER_PROCESSING_DURATION.name())
                    .description(LedgerMetrics.LEDGER_PROCESSING_DURATION.description())
                    .tag(LedgerMetricTags.OPERATION, operation)
                    .tag(LedgerMetricTags.INGRESS, ingress)
                    .tag(LedgerMetricTags.OUTCOME, outcome.outcome())
                    .register(registry));
        }
    }

    private void recordRequest(String operation, String ingress, BusinessOutcome outcome) {
        Counter.builder(LedgerMetrics.LEDGER_REQUESTS.name())
                .description(LedgerMetrics.LEDGER_REQUESTS.description())
                .tag(LedgerMetricTags.OPERATION, operation)
                .tag(LedgerMetricTags.INGRESS, ingress)
                .tag(LedgerMetricTags.OUTCOME, outcome.outcome())
                .tag(LedgerMetricTags.ERROR_CODE, outcome.errorCode())
                .register(registry)
                .increment();
    }

    private Counter counter(MetricDef metric) {
        return Counter.builder(metric.name())
                .description(metric.description())
                .register(registry);
    }

    private void recordPostTransactionFacts(String ingress, PostTransactionResult result) {
        if (result == null || !result.isSuccess()) {
            return;
        }
        // These counters are emitted only after the processor has classified the result.
        if (result.isCreated()) {
            recordLedgerTransactionCreated(result.entryCount());
        } else if (result.isIdempotentReplay()) {
            recordIdempotentTransaction(ingress, sourceTag(result.idempotentSource()));
        }
    }

    private BusinessOutcome fromPostTransactionResult(PostTransactionResult result) {
        if (result == null) {
            return BusinessOutcome.error(LedgerServiceErrorCode.SERVER_ERROR.getMessage());
        }
        if (result.isSuccess()) {
            return BusinessOutcome.success();
        }
        LedgerServiceErrorCode errorCode = result.errorCode();
        return BusinessOutcome.error(errorCode == null ? LedgerServiceErrorCode.SERVER_ERROR.getMessage() : errorCode.getMessage());
    }

    private BusinessOutcome fromResult(Result<?> result) {
        if (result == null) {
            return BusinessOutcome.error(LedgerServiceErrorCode.SERVER_ERROR.getMessage());
        }
        if (result.isSuccess()) {
            return BusinessOutcome.success();
        }
        return BusinessOutcome.error(LedgerBoundaryErrorMapper.toLedgerErrorCode(result).getMessage());
    }

    private String sourceTag(PostTransactionResult.IdempotentSource source) {
        if (source == PostTransactionResult.IdempotentSource.REDIS) {
            return LedgerMetricTagValues.Sources.REDIS;
        }
        if (source == PostTransactionResult.IdempotentSource.DB) {
            return LedgerMetricTagValues.Sources.DB;
        }
        return LedgerMetricTagValues.UNKNOWN;
    }

    private record BusinessOutcome(String outcome, String errorCode) {
        static BusinessOutcome success() {
            return new BusinessOutcome(
                    LedgerMetricTagValues.Outcomes.SUCCESS,
                    LedgerMetricTagValues.ErrorCodes.OK
            );
        }

        static BusinessOutcome error(String errorCode) {
            return new BusinessOutcome(
                    LedgerMetricTagValues.Outcomes.ERROR,
                    errorCode == null ? LedgerMetricTagValues.UNKNOWN : errorCode
            );
        }
    }
}
