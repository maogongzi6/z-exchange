package com.exchange.common.redis.cache.component.support;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RawCacheDeleter {
    private final BaseRedisSupport<String> baseRedisSupport;

    public Result<Boolean> delete(String key) {
        Boolean deleted = baseRedisSupport.delete(key);
        return Result.success(deleted);
    }
}
