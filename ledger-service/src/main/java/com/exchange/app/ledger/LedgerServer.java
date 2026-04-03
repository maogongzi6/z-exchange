package com.exchange.app.ledger;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.register.appside.AppSideCacheRegister;
import com.exchange.common.redis.cache.register.simple.SimpleCacheRegister;
import com.exchange.common.redis.cache.register.version.VersionCacheRegister;
import com.exchange.common.redis.register.RedissonRegister;
import com.exchange.common.redis.idemp.register.RedisIdempRegister;
import com.exchange.common.db.register.CommonDbComponentRegister;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScans({
        @MapperScan("com.exchange.app.ledger.dao.mapper"),
        @MapperScan("com.exchange.common.outbox.dao.mapper")
})
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {
                "com.exchange.app.ledger",
                "com.exchange.common.outbox",
                "com.exchange.common.kafka",
                "com.exchange.common.redis.cache.component",
                "com.exchange.common.redis.config",
                "com.exchange.common.component",
        },
        scanBasePackageClasses = {
                CommonDbComponentRegister.class,
                RedissonRegister.class,
                BaseRedisSupport.class,
                SimpleCacheRegister.class,
                VersionCacheRegister.class,
                AppSideCacheRegister.class,
                RedisIdempRegister.class,
        })
public class LedgerServer {
    public static void main(String[] args) {
        SpringApplication.run(LedgerServer.class, args);
    }
}
