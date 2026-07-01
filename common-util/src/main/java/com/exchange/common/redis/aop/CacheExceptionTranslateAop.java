package com.exchange.common.redis.aop;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.cache.component.support.ScriptExecutor;
import com.exchange.common.result.error.RedisErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.client.RedisException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@Order(0)
@Aspect
public class CacheExceptionTranslateAop {
    @Around("@within(CacheExceptionTranslate) || @annotation(CacheExceptionTranslate)")
    public Object translate(ProceedingJoinPoint pt) throws Throwable {
        try {
            return pt.proceed();
        } catch (QueryTimeoutException e) {
            throw translate(pt, RedisErrorCode.TIMEOUT, e);
        } catch (PermissionDeniedDataAccessException e) {
            throw translate(pt, RedisErrorCode.ACCESS, e);
        } catch (DataAccessResourceFailureException e) {
            throw translate(pt, RedisErrorCode.CONNECTION, e);
        } catch (DataAccessException | RedisException e) {
            throw translate(pt, classify(e, isScriptOperation(pt)), e);
        }
    }

    private CacheException translate(ProceedingJoinPoint pt, RedisErrorCode errorCode, Throwable cause) {
        String operation = pt.getSignature().toShortString();
        log.error("cache operation failed, operation: {}, errorCode: {}", operation, errorCode, cause);
        return new CacheException(errorCode, "cache operation failed: " + operation, cause);
    }

    private RedisErrorCode classify(Throwable throwable, boolean scriptOperation) {
        Throwable rootCause = rootCauseOf(throwable);
        String classNames = throwable.getClass().getName() + " " + rootCause.getClass().getName();

        if (throwable instanceof QueryTimeoutException
                || rootCause instanceof TimeoutException
                || rootCause instanceof SocketTimeoutException
                || classNames.contains("Timeout")) {
            return RedisErrorCode.TIMEOUT;
        }
        if (throwable instanceof PermissionDeniedDataAccessException
                || classNames.contains("Permission")
                || classNames.contains("AccessDenied")
                || classNames.contains("Auth")) {
            return RedisErrorCode.ACCESS;
        }
        if (throwable instanceof DataAccessResourceFailureException
                || rootCause instanceof IOException
                || classNames.contains("Connection")) {
            return RedisErrorCode.CONNECTION;
        }
        if (scriptOperation) {
            return RedisErrorCode.SCRIPT;
        }
        return RedisErrorCode.UNEXPECTED_INTERNAL;
    }

    private boolean isScriptOperation(ProceedingJoinPoint pt) {
        return pt.getTarget() instanceof ScriptExecutor;
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
