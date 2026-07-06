package com.exchange.common.db.exception;

import com.exchange.common.exception.DbException;
import com.exchange.common.result.error.DbErrorCode;
import com.exchange.common.utils.ThrowableUtils;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionTimedOutException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLTransientException;
import java.util.concurrent.TimeoutException;

public class DbExceptionTranslator {
    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;
    private static final int MYSQL_DEADLOCK = 1213;

    public DbException translate(String operation, Throwable throwable) {
        if (throwable instanceof DbException dbException) {
            return dbException;
        }

        DbErrorCode errorCode = classify(throwable);
        if (errorCode == null) {
            return null;
        }
        return DbException.create(errorCode, boundedOperation(operation), throwable);
    }

    public DbErrorCode classify(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        SQLException sqlException = ThrowableUtils.findCause(throwable, SQLException.class);
        Throwable rootCause = ThrowableUtils.rootCauseOf(throwable);

        // Keep classification conservative:
        // 1. typed Spring/JDBC exceptions;
        // 2. typed root causes;
        // 3. SQL state or vendor error code.
        //
        // Do not classify by free-form exception messages here. Driver/pool
        // messages vary and can create false positives. Add message fallback
        // later only when logs show a repeated missed DB failure pattern.
        if (isTransientLockFailure(throwable, sqlException)) {
            return DbErrorCode.DB_TRANSIENT;
        }
        if (isExplicitTimeout(throwable, rootCause)) {
            return DbErrorCode.DB_TIMEOUT;
        }
        if (isUnavailable(throwable, rootCause)) {
            return DbErrorCode.DB_UNAVAILABLE;
        }
        if (isInternalDbFailure(throwable, rootCause, sqlException)) {
            return DbErrorCode.DB_INTERNAL;
        }

        return null;
    }

    private boolean isTransientLockFailure(Throwable throwable, SQLException sqlException) {
        return throwable instanceof DeadlockLoserDataAccessException
                || throwable instanceof CannotAcquireLockException
                || throwable instanceof PessimisticLockingFailureException
                || ThrowableUtils.rootCauseOf(throwable) instanceof SQLTransactionRollbackException
                || hasSqlErrorCode(sqlException, MYSQL_LOCK_WAIT_TIMEOUT)
                || hasSqlErrorCode(sqlException, MYSQL_DEADLOCK)
                || hasSqlState(sqlException, "40001");
    }

    private boolean isExplicitTimeout(Throwable throwable, Throwable rootCause) {
        return throwable instanceof QueryTimeoutException
                || throwable instanceof TransactionTimedOutException
                || rootCause instanceof SQLTimeoutException
                || rootCause instanceof SocketTimeoutException
                || rootCause instanceof TimeoutException;
    }

    private boolean isUnavailable(Throwable throwable, Throwable rootCause) {
        return throwable instanceof CannotGetJdbcConnectionException
                || throwable instanceof DataAccessResourceFailureException
                || throwable instanceof CannotCreateTransactionException
                || rootCause instanceof SQLTransientConnectionException
                || rootCause instanceof ConnectException
                || rootCause instanceof NoRouteToHostException;
    }

    private boolean isInternalDbFailure(Throwable throwable, Throwable rootCause, SQLException sqlException) {
        return throwable instanceof TransactionSystemException
                || throwable instanceof BadSqlGrammarException
                || throwable instanceof DataAccessException
                || throwable instanceof TransactionException
                || rootCause instanceof SQLTransientException
                || sqlException != null;
    }

    private boolean hasSqlErrorCode(SQLException sqlException, int errorCode) {
        return sqlException != null && sqlException.getErrorCode() == errorCode;
    }

    private boolean hasSqlState(SQLException sqlException, String sqlState) {
        return sqlException != null && sqlState.equals(sqlException.getSQLState());
    }

    private String boundedOperation(String operation) {
        return operation == null || operation.isBlank() ? "unknown" : operation;
    }
}
