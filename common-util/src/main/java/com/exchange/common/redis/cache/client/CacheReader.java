package com.exchange.common.redis.cache.client;

import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;

public interface CacheReader {
    <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz);
}
