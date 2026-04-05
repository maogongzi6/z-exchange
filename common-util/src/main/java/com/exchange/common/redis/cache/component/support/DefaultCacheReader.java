package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.BaseCacheReadSupport;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.component.codec.CacheDecoder;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultCacheReader<C extends CacheDecoder> {
    private final C cacheDecoder;
    private final BaseCacheReadSupport<String> baseCacheReadSupport;

    public <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz) {
        String value = baseCacheReadSupport.get(key);
        if (value == null) {
            return Results.success();
        }
        try {
            CacheValueInfo<T> cacheValueInfo = cacheDecoder.decode(value, clazz);
            return Results.success(cacheValueInfo);
        } catch (CacheParseException e) {
            log.error("decode exception, decode cache error, key: {}, value: {}, class: {}", key, value, clazz, e);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "parse cache error, key: " + key + ", value: " + value);
        }
    }

//    public Result<CacheValueInfo<String>> get(String key) {
//        return get(key, String.class);
//    }
}
