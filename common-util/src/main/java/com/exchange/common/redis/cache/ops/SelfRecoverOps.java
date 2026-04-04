package com.exchange.common.redis.cache.ops;

import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;

public interface SelfRecoverOps {
    default Result<Boolean> recover(String id) {
        return Results.success();
    }
}
