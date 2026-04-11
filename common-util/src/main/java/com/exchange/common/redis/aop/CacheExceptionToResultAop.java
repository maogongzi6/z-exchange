package com.exchange.common.redis.aop;

import com.exchange.common.exception.CacheException;
import com.exchange.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Aspect
@Order(0)
public class CacheExceptionToResultAop {
    @Around("@within(CacheExceptionToResult) || @annotation(CacheExceptionToResult)")
    public Object downgradeToResult(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (CacheException e) {
            return Result.failure(e.getErrorCode(), e.getMessage());
        }
    }
}
