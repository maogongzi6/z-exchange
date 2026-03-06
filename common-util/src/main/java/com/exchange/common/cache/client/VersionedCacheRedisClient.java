package com.exchange.common.cache.client;

import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.annotation.Bean;
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
public class VersionedCacheRedisClient {
    // value should always be a string in the format of "{version|content}"
    private final BaseRedisSupport<String> baseRedisSupport;
    private final RedissonClient redissonClient;
    private final ResourceLoader resourceLoader;

    private String setIfAbsentOrNewScript;

    @PostConstruct
    public void loadScript() {
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

    public Result<String> get(String key) {
        return Results.success(baseRedisSupport.get(key));
    }

    public Result<Boolean> setIfAbsentOrNewer(String key, String value, long newVersion, Duration ttl) {
        Boolean success = redissonClient.getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, setIfAbsentOrNewScript, RScript.ReturnType.BOOLEAN, Collections.singletonList(key), value, newVersion, ttl.getSeconds());
        return Results.success(success);
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
