package com.exchange.common.db.exception;

import com.exchange.common.result.error.DbErrorCode;
import com.exchange.common.result.error.ErrorCategory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbExceptionTranslatorTest {
    private final DbExceptionTranslator translator = new DbExceptionTranslator();

    @Test
    void mapsConnectionFailureToUnavailable() {
        var exception = new CannotGetJdbcConnectionException("connection unavailable", new SQLException("connection refused"));

        assertEquals(DbErrorCode.DB_UNAVAILABLE, translator.classify(exception));
    }

    @Test
    void mapsTimeoutToDbTimeout() {
        var exception = new QueryTimeoutException("query timeout", new SQLTimeoutException("timeout"));

        assertEquals(DbErrorCode.DB_TIMEOUT, translator.classify(exception));
    }

    @Test
    void mapsDeadlockAndLockWaitTimeoutToTransient() {
        var deadlock = new DeadlockLoserDataAccessException("deadlock", new SQLException("deadlock", "40001", 1213));
        var lockWaitTimeout = new SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205);

        assertEquals(DbErrorCode.DB_TRANSIENT, translator.classify(deadlock));
        assertEquals(DbErrorCode.DB_TRANSIENT, translator.classify(lockWaitTimeout));
    }

    @Test
    void mapsBadSqlToInternal() {
        var exception = new BadSqlGrammarException("select", "select * from", new SQLException("bad sql"));

        assertEquals(DbErrorCode.DB_INTERNAL, translator.classify(exception));
    }

    @Test
    void transientErrorCodeIsUnavailableAndRetryable() {
        assertEquals(ErrorCategory.UNAVAILABLE, DbErrorCode.DB_TRANSIENT.getCategory());
        assertTrue(DbErrorCode.DB_TRANSIENT.isRetryable());
        assertFalse(DbErrorCode.DB_INTERNAL.isRetryable());
    }
}
