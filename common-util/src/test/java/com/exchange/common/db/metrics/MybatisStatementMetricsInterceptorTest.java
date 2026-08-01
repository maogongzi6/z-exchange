package com.exchange.common.db.metrics;

import com.exchange.common.db.exception.DbExceptionTranslator;
import com.exchange.common.metrics.CommonMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MybatisStatementMetricsInterceptorTest {
    private SimpleMeterRegistry registry;
    private Interceptor interceptor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        interceptor = new MybatisStatementMetricsInterceptor(registry, new DbExceptionTranslator());
    }

    @Test
    void recordsMappedStatementDurationWithBoundedTags() throws SQLException {
        assertEquals(1, meteredUpdate(SqlCommandType.UPDATE, null));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "LedgerTxnMapper.updateById")
                .tag("command", "update")
                .tag("outcome", "success")
                .timer()
                .count());
    }

    @Test
    void recordsStatementContextMissingSeparatelyFromUnknownMetadata() throws SQLException {
        StatementHandler handler = meteredStatementHandler(null);

        assertEquals(1, handler.update(null));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "statement_context_missing")
                .tag("command", "unknown")
                .tag("outcome", "success")
                .timer()
                .count());
    }

    @Test
    void recordsNormalizedErrorAndPreservesOriginalException() {
        SQLTimeoutException failure = new SQLTimeoutException("timed out");

        SQLTimeoutException thrown = assertThrows(SQLTimeoutException.class,
                () -> meteredQuery(SqlCommandType.SELECT, failure));

        assertEquals(failure, thrown);
        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "LedgerTxnMapper.updateById")
                .tag("command", "select")
                .tag("outcome", "error")
                .timer()
                .count());
        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_ERRORS.name())
                .tag("statement", "LedgerTxnMapper.updateById")
                .tag("command", "select")
                .tag("error_type", "db_timeout")
                .counter()
                .count());
    }

    @Test
    void separatesSuccessfulAndFailedStatementDurationsByOutcome() throws SQLException {
        assertEquals(1, meteredUpdate(SqlCommandType.UPDATE, null));
        assertThrows(SQLException.class,
                () -> meteredUpdate(SqlCommandType.UPDATE, new SQLException("failed")));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "LedgerTxnMapper.updateById")
                .tag("command", "update")
                .tag("outcome", "success")
                .timer()
                .count());
        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "LedgerTxnMapper.updateById")
                .tag("command", "update")
                .tag("outcome", "error")
                .timer()
                .count());
    }

    @Test
    void collapsesStatementsBeyondTheTimerCacheLimitIntoOneBoundedSeries() throws SQLException {
        interceptor = new MybatisStatementMetricsInterceptor(registry, new DbExceptionTranslator(), 2);

        assertEquals(1, meteredUpdate("FirstMapper.insert", SqlCommandType.INSERT, null));
        assertEquals(1, meteredUpdate("SecondMapper.update", SqlCommandType.UPDATE, null));
        assertEquals(1, meteredUpdate("ThirdMapper.delete", SqlCommandType.DELETE, null));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "cardinality_overflow")
                .tag("command", "unknown")
                .tag("outcome", "success")
                .timer()
                .count());
        assertEquals(3, registry.find(CommonMetrics.DB_OPERATION_DURATION.name()).timers().size());
    }

    @Test
    void recordsMissingMappedStatementMetadataAsUnknown() throws SQLException {
        assertEquals(1, meteredExecutor(meteredStatementHandler(null)).update(null, null));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "unknown")
                .tag("command", "unknown")
                .tag("outcome", "success")
                .timer()
                .count());
    }

    @Test
    void clearsUnconsumedContextAfterExecutorReturns() throws SQLException {
        Executor executor = meteredExecutor(null);
        MappedStatement mappedStatement = mappedStatement(
                "com.exchange.app.ledger.dao.mapper.LedgerTxnMapper.updateById",
                SqlCommandType.UPDATE
        );

        assertEquals(0, executor.update(mappedStatement, null));
        assertEquals(1, meteredStatementHandler(null).update(null));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "statement_context_missing")
                .tag("command", "unknown")
                .tag("outcome", "success")
                .timer()
                .count());
    }

    private int meteredUpdate(SqlCommandType commandType, SQLException failure) throws SQLException {
        return meteredUpdate(
                "com.exchange.app.ledger.dao.mapper.LedgerTxnMapper.updateById",
                commandType,
                failure
        );
    }

    private int meteredUpdate(String statementId,
                              SqlCommandType commandType,
                              SQLException failure) throws SQLException {
        Executor executor = meteredExecutor(meteredStatementHandler(failure));
        return executor.update(mappedStatement(statementId, commandType), null);
    }

    private List<Object> meteredQuery(SqlCommandType commandType, SQLException failure) throws SQLException {
        Executor executor = meteredExecutor(meteredStatementHandler(failure));
        return executor.query(
                mappedStatement(
                        "com.exchange.app.ledger.dao.mapper.LedgerTxnMapper.updateById",
                        commandType
                ),
                null,
                null,
                null
        );
    }

    private StatementHandler meteredStatementHandler(SQLException failure) {
        return (StatementHandler) interceptor.plugin(new TestStatementHandler(failure));
    }

    private Executor meteredExecutor(StatementHandler statementHandler) {
        Executor delegate = (Executor) Proxy.newProxyInstance(
                Executor.class.getClassLoader(),
                new Class<?>[]{Executor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "update" -> statementHandler == null ? 0 : statementHandler.update(null);
                    case "query" -> statementHandler == null ? List.of() : statementHandler.query(null, null);
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        return (Executor) interceptor.plugin(delegate);
    }

    private MappedStatement mappedStatement(String statementId, SqlCommandType commandType) {
        Configuration configuration = new Configuration();
        SqlSource sqlSource = parameter -> new BoundSql(configuration, "SELECT 1", List.of(), parameter);
        return new MappedStatement.Builder(
                configuration,
                statementId,
                sqlSource,
                commandType)
                .build();
    }

    private static final class TestStatementHandler implements StatementHandler {
        private final SQLException failure;

        private TestStatementHandler(SQLException failure) {
            this.failure = failure;
        }

        @Override
        public Statement prepare(Connection connection, Integer transactionTimeout) {
            return null;
        }

        @Override
        public void parameterize(Statement statement) {
        }

        @Override
        public void batch(Statement statement) throws SQLException {
            failIfConfigured();
        }

        @Override
        public int update(Statement statement) throws SQLException {
            failIfConfigured();
            return 1;
        }

        @Override
        public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
            failIfConfigured();
            return List.of();
        }

        @Override
        public <E> Cursor<E> queryCursor(Statement statement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BoundSql getBoundSql() {
            return null;
        }

        @Override
        public ParameterHandler getParameterHandler() {
            return null;
        }

        private void failIfConfigured() throws SQLException {
            if (failure != null) {
                throw failure;
            }
        }
    }

}
