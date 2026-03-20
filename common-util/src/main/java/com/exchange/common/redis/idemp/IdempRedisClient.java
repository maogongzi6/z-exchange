package com.exchange.common.redis.idemp;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.idemp.constant.CommonIdempStatus;
import com.exchange.common.redis.idemp.utils.CommonIdempHelper;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class IdempRedisClient {
    private final BaseRedisSupport<String> baseRedisSupport;
    private final RedissonClient redissonClient;
    private final ResourceLoader resourceLoader;

    private String releaseIdempIfOwnedScript;

//    public IdempRedisClient(@Autowired BaseRedisSupport<String> baseRedisSupport, @Autowired RedisTemplate<String, String> redisTemplate, @Autowired RedissonClient redissonClient, @Autowired ResourceLoader resourceLoader) {
//        this.baseRedisSupport = baseRedisSupport;
//        this.redissonClient = redissonClient;
//        this.resourceLoader = resourceLoader;
//    }

    @PostConstruct
    public void loadScript() {
        Resource resource = resourceLoader.getResource("classpath:script/lua/release-idemp-if-owned.lua");
        try {
            try (InputStream inputStream = resource.getInputStream()) {
                releaseIdempIfOwnedScript = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load releaseIdempIfOwned Lua script", e);
        }
    }

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

    public Result<String> releaseIdempIfOwned(String service, String scope, String idempId, String value) {
        String key = CommonIdempHelper.idempKey(service, scope, idempId);
        Object result = redissonClient.getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, releaseIdempIfOwnedScript, RScript.ReturnType.VALUE, Collections.singletonList(key), value);
        return Results.success((String) result);
    }

    public Boolean forceDeleteIdemp(String service, String scope, String idempId) {
        String k = CommonIdempHelper.idempKey(service, scope, idempId);
        return baseRedisSupport.delete(k);
    }
}
