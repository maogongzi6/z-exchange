package com.exchange.common.db.metrics;

import com.exchange.common.db.exception.DbExceptionTranslator;
import com.exchange.common.metrics.CommonMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.cursor.Cursor;
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
        StatementHandler handler = meteredHandler(SqlCommandType.UPDATE, null);

        assertEquals(1, handler.update(null));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "LedgerTxnMapper.updateById")
                .tag("command", "update")
                .tag("outcome", "success")
                .timer()
                .count());
    }

    @Test
    void recordsNormalizedErrorAndPreservesOriginalException() {
        SQLTimeoutException failure = new SQLTimeoutException("timed out");
        StatementHandler handler = meteredHandler(SqlCommandType.SELECT, failure);

        SQLTimeoutException thrown = assertThrows(SQLTimeoutException.class,
                () -> handler.query(null, null));

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
        assertEquals(1, meteredHandler(SqlCommandType.UPDATE, null).update(null));
        assertThrows(SQLException.class,
                () -> meteredHandler(SqlCommandType.UPDATE, new SQLException("failed")).update(null));

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

        assertEquals(1, meteredHandler("FirstMapper.insert", SqlCommandType.INSERT, null).update(null));
        assertEquals(1, meteredHandler("SecondMapper.update", SqlCommandType.UPDATE, null).update(null));
        assertEquals(1, meteredHandler("ThirdMapper.delete", SqlCommandType.DELETE, null).update(null));

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
        StatementHandler delegate = new TestStatementHandler(null, null);
        StatementHandler target = new TestRoutingStatementHandler(delegate);
        StatementHandler handler = (StatementHandler) interceptor.plugin(target);

        assertEquals(1, handler.update(null));

        assertEquals(1, registry.find(CommonMetrics.DB_OPERATION_DURATION.name())
                .tag("statement", "unknown")
                .tag("command", "unknown")
                .tag("outcome", "success")
                .timer()
                .count());
    }

    private StatementHandler meteredHandler(SqlCommandType commandType, SQLException failure) {
        return meteredHandler("com.exchange.app.ledger.dao.mapper.LedgerTxnMapper.updateById", commandType, failure);
    }

    private StatementHandler meteredHandler(String statementId,
                                            SqlCommandType commandType,
                                            SQLException failure) {
        StatementHandler delegate = new TestStatementHandler(mappedStatement(statementId, commandType), failure);
        StatementHandler target = new TestRoutingStatementHandler(delegate);
        return (StatementHandler) interceptor.plugin(target);
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
        private final MappedStatement mappedStatement;
        private final SQLException failure;

        private TestStatementHandler(MappedStatement mappedStatement, SQLException failure) {
            this.mappedStatement = mappedStatement;
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

    // Production MyBatis wraps the concrete handler in RoutingStatementHandler; keep that object shape in tests.
    private static final class TestRoutingStatementHandler implements StatementHandler {
        private final StatementHandler delegate;

        private TestRoutingStatementHandler(StatementHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
            return delegate.prepare(connection, transactionTimeout);
        }

        @Override
        public void parameterize(Statement statement) throws SQLException {
            delegate.parameterize(statement);
        }

        @Override
        public void batch(Statement statement) throws SQLException {
            delegate.batch(statement);
        }

        @Override
        public int update(Statement statement) throws SQLException {
            return delegate.update(statement);
        }

        @Override
        public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
            return delegate.query(statement, resultHandler);
        }

        @Override
        public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
            return delegate.queryCursor(statement);
        }

        @Override
        public BoundSql getBoundSql() {
            return delegate.getBoundSql();
        }

        @Override
        public ParameterHandler getParameterHandler() {
            return delegate.getParameterHandler();
        }
    }
}
