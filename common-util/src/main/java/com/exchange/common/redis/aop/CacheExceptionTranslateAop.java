package com.exchange.common.redis.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@Aspect
public class CacheExceptionTranslateAop {
    @Around("@within(CacheExceptionTranslate) || @annotation(CacheExceptionTranslate)")
    public Object translate(ProceedingJoinPoint pt) throws Throwable {
        // TODO
        try {
            return pt.proceed();
        } catch (Throwable e) {
            throw e;
        }
    }
}
