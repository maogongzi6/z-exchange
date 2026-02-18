package com.exchange.common.cache.client;

import com.exchange.common.cache.constant.CommonIdempStatus;
import com.exchange.common.cache.utils.CommonIdempHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdempRedisClient extends BaseRedisClient<String> {
    public IdempRedisClient(@Autowired RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
    }

    public Boolean setIdempPendingIfAbsent(String service, String scope, String idempId, String hash, Duration ttl) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        String v = CommonIdempHelper.idempValue(CommonIdempStatus.PENDING, hash);
        return setIfAbsent(k, v, ttl);
    }

    public void setIdempDone(String service, String scope, String idempId, String hash, String content, Duration ttl) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        String v = CommonIdempHelper.idempValue(CommonIdempStatus.ACCEPTED, hash, content);
        set(k, v, ttl);
    }

    public String getIdemp(String service, String scope, String idempId) {
        return get(CommonIdempHelper.idempKey(service, scope, idempId));
    }

    public Boolean deleteIdemp(String service, String scope, String idempId) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        return delete(k);
    }
}
