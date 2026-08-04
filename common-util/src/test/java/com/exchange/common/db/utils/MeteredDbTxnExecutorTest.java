package com.exchange.common.db.utils;

import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.DbErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeteredDbTxnExecutorTest {
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    void recordsSuccessfulResult() {
        DbTxnExecutor executor = metered(new PassThroughDbTxnExecutor());

        Result<Void> result = executor.executeWithDefault(Result::success);

        assertEquals(Result.success(), result);
        assertEquals(1, timerCount("success"));
        assertEquals(0, timerCount("error"));
    }

    @Test
    void recordsFailedResultAsError() {
        DbTxnExecutor executor = metered(new PassThroughDbTxnExecutor());

        Result<Void> result = executor.executeWithDefault(
                () -> Result.failure(DbErrorCode.DB_INTERNAL));

        assertEquals(Result.failure(DbErrorCode.DB_INTERNAL), result);
        assertEquals(0, timerCount("success"));
        assertEquals(1, timerCount("error"));
    }

    @Test
    void recordsDelegateExceptionAsError() {
        DbTxnExecutor executor = metered(new ThrowingDbTxnExecutor());

        assertThrows(IllegalStateException.class,
                () -> executor.executeWithDefault(Result::success));

        assertEquals(0, timerCount("success"));
        assertEquals(1, timerCount("error"));
    }

    private DbTxnExecutor metered(DbTxnExecutor delegate) {
        return new MeteredDbTxnExecutor(delegate, registry);
    }

    private double timerCount(String outcome) {
        return registry.find(CommonMetrics.DB_TRANSACTION_DURATION.name())
                .tag("outcome", outcome)
                .timer()
                .count();
    }

    private static class PassThroughDbTxnExecutor implements DbTxnExecutor {
        @Override
        public <T extends Result<?>> T executeWithDefault(java.util.function.Supplier<T> supplier) {
            return supplier.get();
        }
    }

    private static class ThrowingDbTxnExecutor implements DbTxnExecutor {
        @Override
        public <T extends Result<?>> T executeWithDefault(java.util.function.Supplier<T> supplier) {
            throw new IllegalStateException("transaction failed");
        }
    }
}
