package com.exchange.app.wallet;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.register.ReadableCacheRegister;
import com.exchange.common.redis.cache.register.simple.SimpleCacheRegister;
import com.exchange.common.redis.cache.register.version.VersionCacheRegister;
import com.exchange.common.redis.idemp.register.RedisIdempRegister;
import com.exchange.common.db.register.CommonDbComponentRegister;
import com.exchange.common.redis.register.RedissonRegister;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScans({
        @MapperScan("com.exchange.app.wallet.dao.mapper"),
        @MapperScan("com.exchange.common.outbox.dao.mapper")
})
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {
                "com.exchange.app.wallet",
                "com.exchange.common.outbox",
                "com.exchange.common.kafka",
                "com.exchange.common.redis.register",
                "com.exchange.common.redis.cache.component",
                "com.exchange.common.component"
        },
        scanBasePackageClasses = {
                CommonDbComponentRegister.class,
                RedissonRegister.class,
                BaseRedisSupport.class,
                ReadableCacheRegister.class,
                SimpleCacheRegister.class,
                VersionCacheRegister.class,
                RedisIdempRegister.class,
        })
public class WalletServer {
    public static void main(String[] args) {
        SpringApplication.run(WalletServer.class, args);
    }
}
