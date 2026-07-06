package com.exchange.common.exception;

import com.exchange.common.result.error.DbErrorCode;

public class DbException extends CustomizedException {
    public DbException(DbErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public DbErrorCode getDbErrorCode() {
        return (DbErrorCode) getErrorCode();
    }

    public boolean isRetryable() {
        return getDbErrorCode().isRetryable();
    }

    public static DbException internal(String operation, Throwable cause) {
        return create(DbErrorCode.DB_INTERNAL, operation, cause);
    }

    public static DbException timeout(String operation, Throwable cause) {
        return create(DbErrorCode.DB_TIMEOUT, operation, cause);
    }

    public static DbException unavailable(String operation, Throwable cause) {
        return create(DbErrorCode.DB_UNAVAILABLE, operation, cause);
    }

    public static DbException transientFailure(String operation, Throwable cause) {
        return create(DbErrorCode.DB_TRANSIENT, operation, cause);
    }

    public static DbException create(DbErrorCode errorCode, String operation, Throwable cause) {
        return new DbException(errorCode, "db operation failed: " + operation, cause);
    }
}
