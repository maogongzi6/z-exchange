package com.exchange.common.cache.client;

import com.exchange.common.redis.idemp.IdempRedisClient;
import com.exchange.common.redis.idemp.utils.CommonIdempHelper;
import com.exchange.common.result.Result;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = IdempRedisClientIT.TestApp.class,
        properties = {
                // match your env (adjust if you want to override)
                "spring.redis.host=192.168.52.100",
                "spring.redis.port=6379",
                "spring.redisson.config=classpath:redisson.yml"
        }
)
@ActiveProfiles("test")
class IdempRedisClientIT {

    @Autowired
    IdempRedisClient idempRedisClient;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Test
    void claimIdempIfAbsent_shouldClaimFirstTime_andRejectSecondTime() {
        String service = "svc";
        String scope = "scope";
        String idempId = "id-" + UUID.randomUUID();
        String hash = "hash-" + UUID.randomUUID();
        String token = "token-" + UUID.randomUUID();

        boolean first = Boolean.TRUE.equals(
                idempRedisClient.claimIdempIfAbsent(service, scope, idempId, hash, token, Duration.ofSeconds(30))
        );
        boolean second = Boolean.TRUE.equals(
                idempRedisClient.claimIdempIfAbsent(service, scope, idempId, hash, token, Duration.ofSeconds(30))
        );

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void releaseIdempIfOwned_shouldReturnEmpty_whenKeyAbsent() {
        String service = "svc";
        String scope = "scope";
        String idempId = "id-" + UUID.randomUUID();
        String token = "token-" + UUID.randomUUID();
        String hash = "hash-" + UUID.randomUUID();

        // Ensure absent
        String key = CommonIdempHelper.idempKey(service, scope, idempId);
        redisTemplate.delete(key);

        Result<String> res = idempRedisClient.releaseIdempIfOwned(service, scope, idempId, CommonIdempHelper.idempPendingValue(hash, token));

        assertThat(res).isNotNull();
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getValue()).isIn("", null);
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void releaseIdempIfOwned_shouldNotDelete_whenTokenDoesNotMatch() {
        String service = "svc";
        String scope = "scope";
        String idempId = "id-" + UUID.randomUUID();

        String correctToken = "token-" + UUID.randomUUID();
        String wrongToken = "token-" + UUID.randomUUID();
        String hash = "hash-" + UUID.randomUUID();

        String key = CommonIdempHelper.idempKey(service, scope, idempId);
        redisTemplate.delete(key);

        // Create a claimed entry (uses your existing flow to ensure the stored format matches the Lua script)
        Boolean claimed = idempRedisClient.claimIdempIfAbsent(
                service, scope, idempId, hash, correctToken, Duration.ofSeconds(30)
        );
        assertThat(claimed).isTrue();
        assertThat(redisTemplate.hasKey(key)).isTrue();

        Result<String> res = idempRedisClient.releaseIdempIfOwned(service, scope, idempId, CommonIdempHelper.idempPendingValue(hash, wrongToken));

        assertThat(res).isNotNull();
        assertThat(res.isSuccess()).isTrue();

        // Expect no deletion when not owned
        assertThat(redisTemplate.hasKey(key)).isTrue();
    }

    @Test
    void releaseIdempIfOwned_shouldDelete_whenTokenMatches() {
        String service = "svc";
        String scope = "scope";
        String idempId = "id-" + UUID.randomUUID();

        String token = "token-" + UUID.randomUUID();
        String hash = "hash-" + UUID.randomUUID();

        String key = CommonIdempHelper.idempKey(service, scope, idempId);
        redisTemplate.delete(key);

        Boolean claimed = idempRedisClient.claimIdempIfAbsent(
                service, scope, idempId, hash, token, Duration.ofSeconds(30)
        );
        assertThat(claimed).isTrue();
        assertThat(redisTemplate.hasKey(key)).isTrue();

        Result<String> res = idempRedisClient.releaseIdempIfOwned(service, scope, idempId, CommonIdempHelper.idempPendingValue(hash, token));

        assertThat(res).isNotNull();
        assertThat(res.isSuccess()).isTrue();
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }


    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import({IdempRedisClient.class, TestRedisSerdeConfig.class})
    static class TestApp {
        // Intentionally empty: this is the minimal Spring Boot test application.
    }

    /**
     * Ensures RedisTemplate<String,String> is configured with string serializers.
     * If your project already provides this bean via auto-config + properties, you can remove this config.
     */
    @TestConfiguration
    static class TestRedisSerdeConfig {
        @Bean
        @Primary
        RedisTemplate<String, String> redisTemplate(org.springframework.data.redis.connection.RedisConnectionFactory factory) {
            RedisTemplate<String, String> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);
            template.setKeySerializer(org.springframework.data.redis.serializer.RedisSerializer.string());
            template.setValueSerializer(org.springframework.data.redis.serializer.RedisSerializer.string());
            template.setHashKeySerializer(org.springframework.data.redis.serializer.RedisSerializer.string());
            template.setHashValueSerializer(org.springframework.data.redis.serializer.RedisSerializer.string());
            template.afterPropertiesSet();
            return template;
        }
    }
}
