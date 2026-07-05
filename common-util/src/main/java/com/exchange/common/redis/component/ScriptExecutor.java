package com.exchange.common.redis.component;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.aop.CacheExceptionTranslate;
import com.exchange.common.result.error.RedisErrorCode;
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
public class ScriptExecutor implements RedisScriptExecutor {
    private final ResourceLoader resourceLoader;

    private String setIfAbsentOrNewerScript;
    private String releaseIdempIfOwnedScript;

    @PostConstruct
    private void loadScript() {
        setIfAbsentOrNewerScript = loadLua("classpath:script/lua/set-if-absent-or-newer.lua",
                "setIfAbsentOrNewer");
        releaseIdempIfOwnedScript = loadLua("classpath:script/lua/release-idemp-if-owned.lua",
                "releaseIdempIfOwned");
    }

    @Override
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

    @Override
    public String releaseIdempIfOwned(RedissonClient redissonClient, String key, String expectedValue) {
        Object result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                releaseIdempIfOwnedScript,
                RScript.ReturnType.VALUE,
                Collections.singletonList(key),
                expectedValue);
        return result == null ? "" : (String) result;
    }

    private String loadLua(String location, String scriptName) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CacheException(RedisErrorCode.CONFIGURATION,
                    "failed to load " + scriptName + " Lua script", e);
        }
    }
}
