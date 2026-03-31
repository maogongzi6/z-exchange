package com.exchange.common.redis.register;

import com.exchange.common.redis.config.RedissonProperties;
import org.redisson.Redisson;
import org.redisson.api.RClientSideCaching;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.ClientSideCachingOptions;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties({RedissonProperties.class})
public class NormalRedissonRegister {

    @Primary
    @Bean(name = "normalRedissonClient", destroyMethod = "shutdown")
    public RedissonClient normalRedissonClient(RedisProperties redisProperties, RedissonProperties redissonProperties) {
        Config config = new Config();
        RedissonProperties.ClientProperties redissonClientProperties = redissonProperties.getNormalProperties();

        SingleServerConfig server = buildSingleServerConfig(config, redisProperties, redissonClientProperties);

        applyAuth(server, redisProperties);

        return Redisson.create(config);
    }

    @Lazy
    @Bean(name = "clientCacheRedissonClient", destroyMethod = "shutdown")
    public RedissonClient clientCacheRedissonClient(RedisProperties redisProperties, RedissonProperties redissonProperties) {
        Config config = new Config();
        RedissonProperties.ClientProperties redissonClientProperties = redissonProperties.getClientSideCacheProperties();

        // Native Redis server-assisted client-side caching requires RESP3
        config.setProtocol(Protocol.RESP3);

        SingleServerConfig server = buildSingleServerConfig(config, redisProperties, redissonClientProperties);

        applyAuth(server, redisProperties);

        return Redisson.create(config);
    }

    @Bean(name = "clientSideCaching", destroyMethod = "destroy")
    public RClientSideCaching clientSideCaching(
            @Qualifier("clientCacheRedissonClient") RedissonClient clientCacheRedissonClient) {

        return clientCacheRedissonClient.getClientSideCaching(
                ClientSideCachingOptions.defaults()
        );
    }

    private SingleServerConfig buildSingleServerConfig(Config config,
                                                       RedisProperties redisProperties,
                                                       RedissonProperties.ClientProperties redissonClientProperties) {
        return config.useSingleServer()
                .setAddress(buildAddress(redisProperties))
                .setDatabase(redisProperties.getDatabase())
                .setClientName(redissonClientProperties.getClientName())
                .setConnectionMinimumIdleSize(redissonClientProperties.getConnectionMinimumIdleSize())
                .setConnectionPoolSize(redissonClientProperties.getConnectionPoolSize())
                .setTimeout((int) redissonClientProperties.getTimeout().toMillis())
                .setConnectTimeout((int) redissonClientProperties.getConnectionTimeout().toMillis());
    }

    private static String buildAddress(RedisProperties redisProperties) {
        String scheme = redisProperties.isSsl() ? "rediss://" : "redis://";
        return scheme + redisProperties.getHost() + ":" + redisProperties.getPort();
    }

    private static void applyAuth(SingleServerConfig server, RedisProperties redisProperties) {
        if (redisProperties.getUsername() != null && !redisProperties.getUsername().isBlank()) {
            server.setUsername(redisProperties.getUsername());
        }
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            server.setPassword(redisProperties.getPassword());
        }
    }
}