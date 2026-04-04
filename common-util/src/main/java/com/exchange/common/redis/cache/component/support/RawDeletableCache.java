package com.exchange.common.redis.cache.component.support;

import com.exchange.common.utils.result.Result;

public interface RawDeletableCache {
    Result<Boolean> delete(String key);
}
