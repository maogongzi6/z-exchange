package com.exchange.common.redis.aop;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
// unused, replaced by explicit degrade in StrategyFactory
public @interface CacheExceptionToResult {
}
