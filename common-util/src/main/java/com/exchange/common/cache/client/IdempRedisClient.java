package com.exchange.common.cache.client;

import com.exchange.common.cache.constant.CommonIdempStatus;
import com.exchange.common.cache.utils.CommonIdempHelper;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class IdempRedisClient extends BaseRedisClient<String> {
    private final RedissonClient redissonClient;
    private final ResourceLoader resourceLoader;

    public IdempRedisClient(@Autowired RedisTemplate<String, String> redisTemplate, @Autowired RedissonClient redissonClient, @Autowired ResourceLoader resourceLoader) {
        super(redisTemplate);
        this.redissonClient = redissonClient;
        this.resourceLoader = resourceLoader;
    }

    public Boolean claimIdempIfAbsent(String service, String scope, String idempId, String hash, String token, Duration ttl) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        String v = CommonIdempHelper.idempPendingValue(hash, token);
        return setIfAbsent(k, v, ttl);
    }

    public void markIdempDone(String service, String scope, String idempId, String hash, String token, String content, Duration ttl) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        String v = CommonIdempHelper.idempValue(CommonIdempStatus.ACCEPTED, hash, token, content);
        set(k, v, ttl);
    }

    public String getIdemp(String service, String scope, String idempId) {
        return get(CommonIdempHelper.idempKey(service, scope, idempId));
    }

    public Result<String> releaseIdempIfOwned(String service, String scope, String idempId, String value) {
        String key = CommonIdempHelper.idempKey(service, scope, idempId);
        String scriptLocation = "classpath:script/lua/release-idemp-if-owned.lua";

        try {
            Resource resource = resourceLoader.getResource(scriptLocation);
            byte[] bytes = resource.getInputStream().readAllBytes();
            String script = new String(bytes, StandardCharsets.UTF_8);
            Object result = redissonClient.getScript(StringCodec.INSTANCE)
                    .eval(RScript.Mode.READ_ONLY, script, RScript.ReturnType.VALUE, Collections.singletonList(key), value);
            return Results.success((String) result);
        } catch (Exception e) {
            log.error("Error executing releaseIdempIfOwned Lua script", e);
            return Results.fail(CommonErrorCode.LUA_SCRIPT_EXECUTE_ERROR, "Error executing releaseIdempIfOwned Lua script");
        }
    }

    public Boolean forceDeleteIdemp(String service, String scope, String idempId) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        return delete(k);
    }
}
