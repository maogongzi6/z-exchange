package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.aop.CacheExceptionTranslate;
import com.exchange.common.result.error.CacheErrorCode;
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
@CacheExceptionTranslate
public class ScriptExecutor {
    private final ResourceLoader resourceLoader;

    private String setIfAbsentOrNewerScript;

    @PostConstruct
    private void loadScript() {
        try {
            Resource resource = resourceLoader.getResource("classpath:script/lua/set-if-absent-or-newer.lua");
            try (InputStream inputStream = resource.getInputStream()) {
                setIfAbsentOrNewerScript = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new CacheException(CacheErrorCode.CONFIGURATION,
                    "failed to load setIfAbsentOrNewer Lua script", e);
        }
    }

    public Boolean setIfAbsentOrNewer(RedissonClient redissonClient, String key, String value, long newVersion, Duration ttl) {
        return redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                setIfAbsentOrNewerScript,
                RScript.ReturnType.BOOLEAN,
                Collections.singletonList(key),
                value,
                newVersion,
                ttl.toMillis());
    }
}
