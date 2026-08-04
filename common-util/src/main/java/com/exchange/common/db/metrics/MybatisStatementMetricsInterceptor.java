package com.exchange.common.db.metrics;

import com.exchange.common.db.exception.DbExceptionTranslator;
import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.result.error.DbErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                CacheKey.class, BoundSql.class
        }),
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class MybatisStatementMetricsInterceptor implements Interceptor {
    private static final int DEFAULT_MAX_CACHED_TIMERS = 512;
    // Preserve an observation when MyBatis metadata is missing or malformed instead of dropping the metric.
    private static final StatementKey UNKNOWN_STATEMENT = new StatementKey(
            CommonMetricTagValues.UNKNOWN,
            CommonMetricTagValues.DbCommands.UNKNOWN
    );
    // A statement without matching Executor metadata indicates an unexpected MyBatis instrumentation path.
    private static final StatementKey MISSING_STATEMENT_CONTEXT = new StatementKey(
            CommonMetricTagValues.STATEMENT_CONTEXT_MISSING,
            CommonMetricTagValues.DbCommands.UNKNOWN
    );
    // Collapse unseen statements after timer admission is exhausted, bounding registry cardinality and memory use.
    private static final StatementKey OVERFLOW_STATEMENT = new StatementKey(
            CommonMetricTagValues.CARDINALITY_OVERFLOW,
            CommonMetricTagValues.DbCommands.UNKNOWN
    );

    private final MeterRegistry meterRegistry;
    private final DbExceptionTranslator exceptionTranslator;
    private final int maxCachedTimers;
    private final Map<StatementKey, Timer> successTimers = new ConcurrentHashMap<>();
    private final Map<StatementKey, Timer> errorTimers = new ConcurrentHashMap<>();
    private final AtomicInteger cachedTimerCount = new AtomicInteger();
    // Executor exposes MappedStatement through its public API, while StatementHandler provides the JDBC-adjacent
    // timing boundary. This one-shot context transfers metadata between those callbacks on the same DB thread.
    private final ThreadLocal<StatementKey> statementContext = new ThreadLocal<>();

    public MybatisStatementMetricsInterceptor(MeterRegistry meterRegistry,
                                              DbExceptionTranslator exceptionTranslator) {
        this(meterRegistry, exceptionTranslator, DEFAULT_MAX_CACHED_TIMERS);
    }

    MybatisStatementMetricsInterceptor(MeterRegistry meterRegistry,
                                       DbExceptionTranslator exceptionTranslator,
                                       int maxCachedTimers) {
        if (maxCachedTimers < 1) {
            throw new IllegalArgumentException("maxCachedTimers must be positive");
        }
        this.meterRegistry = meterRegistry;
        this.exceptionTranslator = exceptionTranslator;
        this.maxCachedTimers = maxCachedTimers;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof Executor) {
            return interceptExecutor(invocation);
        }
        return interceptStatement(invocation);
    }

    private Object interceptExecutor(Invocation invocation) throws Throwable {
        // Capture the key here because Executor exposes MappedStatement as a public argument before it creates the
        // StatementHandler. Extracting it later from StatementHandler would require reflection over private MyBatis
        // fields and plugin proxies, making metrics dependent on internal implementation details.
        statementContext.set(statementKeySafely(mappedStatementArgument(invocation)));
        try {
            return invocation.proceed();
        } finally {
            // StatementHandler normally consumes the value. This cleanup covers cache hits and failures that occur
            // before JDBC execution, preventing context leakage on reused request or worker threads.
            statementContext.remove();
        }
    }

    private Object interceptStatement(Invocation invocation) throws Throwable {
        StatementKey key = consumeStatementKey();
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            Object result = invocation.proceed();
            success = true;
            return result;
        } catch (Throwable throwable) {
            recordErrorSafely(key, throwable);
            throw throwable;
        } finally {
            recordDurationSafely(key, success, System.nanoTime() - startedAt);
        }
    }

    private MappedStatement mappedStatementArgument(Invocation invocation) {
        Object[] args = invocation.getArgs();
        if (args.length == 0 || !(args[0] instanceof MappedStatement mappedStatement)) {
            return null;
        }
        return mappedStatement;
    }

    private StatementKey consumeStatementKey() {
        StatementKey key = statementContext.get();
        // Consume before JDBC execution. A nested MyBatis query will install and consume its own statement key.
        statementContext.remove();
        return key == null ? MISSING_STATEMENT_CONTEXT : key;
    }

    private Timer createTimer(StatementKey key, String outcome) {
        return Timer.builder(CommonMetrics.DB_OPERATION_DURATION.name())
                .description(CommonMetrics.DB_OPERATION_DURATION.description())
                .tag(CommonMetricTags.STATEMENT, key.statement())
                .tag(CommonMetricTags.COMMAND, key.command())
                .tag(CommonMetricTags.OUTCOME, outcome)
                .register(meterRegistry);
    }

    private void recordDurationSafely(StatementKey key, boolean success, long durationNanos) {
        try {
            timer(normalize(key), success).record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // Observability failure must not change the SQL operation result or transaction outcome.
            // If diagnostics become necessary, add rate-limited logging rather than logging every DB operation.
        }
    }

    private Timer timer(StatementKey key, boolean success) {
        Map<StatementKey, Timer> timers = success ? successTimers : errorTimers;
        Timer cached = timers.get(key);
        if (cached != null) {
            return cached;
        }

        if (cachedTimerCount.get() >= maxCachedTimers) {
            return overflowTimer(timers, success);
        }

        String outcome = success ? CommonMetricTagValues.Outcomes.SUCCESS : CommonMetricTagValues.Outcomes.ERROR;
        Timer candidate = createTimer(key, outcome);
        Timer existing = timers.putIfAbsent(key, candidate);
        if (existing != null) {
            return existing;
        }

        // Admission is intentionally approximate and lock-free. Concurrent first-time keys may exceed the limit
        // slightly; this trades strict accuracy for lower contention on the DB hot path.
        cachedTimerCount.incrementAndGet();
        return candidate;
    }

    private Timer overflowTimer(Map<StatementKey, Timer> timers, boolean success) {
        Timer cached = timers.get(OVERFLOW_STATEMENT);
        if (cached != null) {
            return cached;
        }
        String outcome = success ? CommonMetricTagValues.Outcomes.SUCCESS : CommonMetricTagValues.Outcomes.ERROR;
        Timer candidate = createTimer(OVERFLOW_STATEMENT, outcome);
        Timer existing = timers.putIfAbsent(OVERFLOW_STATEMENT, candidate);
        return existing == null ? candidate : existing;
    }

    private void recordErrorSafely(StatementKey key, Throwable throwable) {
        try {
            StatementKey normalizedKey = normalize(key);
            DbErrorCode errorCode = exceptionTranslator.classify(throwable);
            String errorType = errorCode == null ? CommonMetricTagValues.UNKNOWN : errorCode.getMessage();
            // Errors are exceptional, so an additional application-side counter cache is not justified.
            Counter.builder(CommonMetrics.DB_OPERATION_ERRORS.name())
                    .description(CommonMetrics.DB_OPERATION_ERRORS.description())
                    .tag(CommonMetricTags.STATEMENT, normalizedKey.statement())
                    .tag(CommonMetricTags.COMMAND, normalizedKey.command())
                    .tag(CommonMetricTags.ERROR_TYPE, errorType)
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException ignored) {
            // Preserve the original SQL exception even if metric classification or recording fails.
            // If diagnostics become necessary, add rate-limited logging rather than logging every DB operation.
        }
    }

    private StatementKey statementKeySafely(MappedStatement mappedStatement) {
        try {
            if (mappedStatement == null) {
                return UNKNOWN_STATEMENT;
            }
            return new StatementKey(shortStatementId(mappedStatement.getId()), command(mappedStatement.getSqlCommandType()));
        } catch (RuntimeException ignored) {
            // MyBatis internals or malformed metadata must not affect execution of the intercepted statement.
            // If diagnostics become necessary, add rate-limited logging rather than logging every DB operation.
            return UNKNOWN_STATEMENT;
        }
    }

    private StatementKey normalize(StatementKey key) {
        return key == null ? UNKNOWN_STATEMENT : key;
    }

    private String shortStatementId(String statementId) {
        if (statementId == null || statementId.isBlank()) {
            return CommonMetricTagValues.UNKNOWN;
        }
        String[] segments = statementId.split("\\.");
        if (segments.length < 2) {
            return statementId;
        }
        return segments[segments.length - 2] + "." + segments[segments.length - 1];
    }

    private String command(SqlCommandType commandType) {
        if (commandType == null) {
            return CommonMetricTagValues.DbCommands.UNKNOWN;
        }
        return switch (commandType) {
            case SELECT -> CommonMetricTagValues.DbCommands.SELECT;
            case INSERT -> CommonMetricTagValues.DbCommands.INSERT;
            case UPDATE -> CommonMetricTagValues.DbCommands.UPDATE;
            case DELETE -> CommonMetricTagValues.DbCommands.DELETE;
            default -> CommonMetricTagValues.DbCommands.UNKNOWN;
        };
    }

    private record StatementKey(String statement, String command) {
    }
}
