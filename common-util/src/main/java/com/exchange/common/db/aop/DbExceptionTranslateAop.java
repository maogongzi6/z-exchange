package com.exchange.common.db.aop;

import com.exchange.common.db.exception.DbExceptionTranslator;
import com.exchange.common.exception.DbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Slf4j
@Aspect
@Order(0)
@RequiredArgsConstructor
public class DbExceptionTranslateAop {
    private final DbExceptionTranslator translator;

    @Around("target(com.exchange.common.db.manager.DbBaseRepository+)")
    public Object translateRepositoryException(ProceedingJoinPoint point) throws Throwable {
        return translate(point);
    }

    @Around("execution(public * com.exchange.common.db.utils.DbTxnExecutor.*(..))")
    public Object translateTxnException(ProceedingJoinPoint point) throws Throwable {
        return translate(point);
    }

    private Object translate(ProceedingJoinPoint point) throws Throwable {
        try {
            return point.proceed();
        } catch (DbException e) {
            throw e;
        } catch (Throwable e) {
            String operation = point.getSignature().toShortString();
            DbException translated = translator.translate(operation, e);
            if (translated == null) {
                throw e;
            }
            log.error(
                    "db operation failed, operation: {}, errorCode: {}, retryable: {}",
                    operation,
                    translated.getDbErrorCode(),
                    translated.isRetryable(),
                    e
            );
            throw translated;
        }
    }
}
