package com.exchange.app.wallet;

import com.exchange.common.redis.register.RedisCacheRegister;
import com.exchange.common.redis.register.RedisIdempRegister;
import com.exchange.common.db.register.CommonDbComponentRegister;
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
        },
        scanBasePackageClasses = {CommonDbComponentRegister.class, RedisCacheRegister.class, RedisIdempRegister.class})
public class WalletServer {
    public static void main(String[] args) {
        SpringApplication.run(WalletServer.class, args);
    }
}
