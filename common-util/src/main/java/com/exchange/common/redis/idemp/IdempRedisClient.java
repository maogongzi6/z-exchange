package com.exchange.common.redis.idemp;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.component.support.ScriptExecutor;
import com.exchange.common.result.Result;
import com.exchange.common.redis.idemp.constant.CommonIdempStatus;
import com.exchange.common.redis.idemp.utils.CommonIdempHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class IdempRedisClient {
    private final BaseRedisSupport<String> baseRedisSupport;
    private final RedissonClient redissonClient;
    private final ScriptExecutor scriptExecutor;

    public Boolean claimIdempIfAbsent(String service, String scope, String idempId, String hash, String token, Duration ttl) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        String v = CommonIdempHelper.idempPendingValue(hash, token);
        return baseRedisSupport.setIfAbsent(k, v, ttl);
    }

    public void markIdempDone(String service, String scope, String idempId, String hash, String token, String content, Duration ttl) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        String v = CommonIdempHelper.idempValue(CommonIdempStatus.ACCEPTED, hash, token, content);
        baseRedisSupport.set(k, v, ttl);
    }

    public String getIdemp(String service, String scope, String idempId) {
        return baseRedisSupport.get(CommonIdempHelper.idempKey(service, scope, idempId));
    }

    // TODO return String instead
    public Result<String> releaseIdempIfOwned(String service, String scope, String idempId, String value) {
        String key = CommonIdempHelper.idempKey(service, scope, idempId);
        return Result.success(scriptExecutor.releaseIdempIfOwned(redissonClient, key, value));
    }

    public Boolean forceDeleteIdemp(String service, String scope, String idempId) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        return baseRedisSupport.delete(k);
    }
}
