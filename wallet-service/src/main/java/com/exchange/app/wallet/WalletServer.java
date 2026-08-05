package com.exchange.app.wallet;

import com.exchange.common.redis.cache.register.CacheSupportRegister;
import com.exchange.common.redis.cache.strategy.impl.StrategyFactory;
import com.exchange.common.redis.idemp.register.RedisIdempRegister;
import com.exchange.common.db.register.CommonDbComponentRegister;
import com.exchange.common.redis.register.BaseRegister;
import com.exchange.common.redis.register.RedissonRegister;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScans({
        @MapperScan("com.exchange.app.wallet.dao.mapper"),
        @MapperScan("com.exchange.common.outbox.dao.mapper")
})
// Common DB translation is implemented as repository/transaction boundary advice.
@EnableAspectJAutoProxy
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
                BaseRegister.class,
                RedissonRegister.class,
                CacheSupportRegister.class,
                RedisIdempRegister.class,
                StrategyFactory.class
        })
public class WalletServer {
    public static void main(String[] args) {
        SpringApplication.run(WalletServer.class, args);
    }
}
