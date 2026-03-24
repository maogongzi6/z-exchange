package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.VersionCodec;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.redis.cache.component.impl.VersionCacheEncoder;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
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
public class VersionedRedisSupport extends ReadRedisSupport {
    private final VersionCacheEncoder cacheEncoder;
    private final ResourceLoader resourceLoader;
    private final RedissonClient redissonClient;

    private String setIfAbsentOrNewScript;

    public VersionedRedisSupport(BaseRedisSupport<String> baseRedisSupport, VersionCodec versionCodec, ResourceLoader resourceLoader, RedissonClient redissonClient) {
        super(versionCodec, baseRedisSupport);
        this.cacheEncoder = versionCodec;
        this.resourceLoader = resourceLoader;
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    private void loadScript() {
        try {
            Resource resource = resourceLoader.getResource("classpath:script/lua/set-if-absent-or-newer.lua");
            try (InputStream inputStream = resource.getInputStream()) {
                setIfAbsentOrNewScript = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // Fail fast: without the script, setIfAbsentOrNewer can't work reliably.
            throw new IllegalStateException("Failed to load setIfAbsentOrNewer Lua script", e);
        }
    }

    public <T> Result<Boolean> setIfAbsentOrNewer(String key, T value, long newVersion, Duration ttl) {
        if (value == null) {
            log.error("setIfAbsentOrNewer value is null, key: {}", key);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "setIfAbsentOrNewer value is null");
        }
        CacheType cacheType = CacheType.fromSource(value);
        return doSetIfAbsentOrNewer(key, value, cacheType, newVersion, ttl);
    }

    public Result<Boolean> setTombstone(String key, long newVersion, Duration ttl) {
        return doSetIfAbsentOrNewer(key, "", CacheType.TOMBSTONE, newVersion, ttl);
    }

    public Result<Boolean> setNegative(String key, long newVersion, Duration ttl) {
        return doSetIfAbsentOrNewer(key, "", CacheType.NEGATIVE, newVersion, ttl);
    }

    private <T> Result<Boolean> doSetIfAbsentOrNewer(String key, T value, CacheType cacheType, long newVersion, Duration ttl) {
        String encoded;
        try {
            encoded = cacheEncoder.encode(value, cacheType, newVersion);
        } catch (CacheParseException e) {
            log.error("failed to encode value, key: {}, value: {}", key, value, e);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "failed to encode value, key: " + key);
        }

        Boolean success = redissonClient.getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, setIfAbsentOrNewScript, RScript.ReturnType.BOOLEAN, Collections.singletonList(key), encoded, newVersion, ttl.toMillis());
        return Results.success(success);
    }
}
