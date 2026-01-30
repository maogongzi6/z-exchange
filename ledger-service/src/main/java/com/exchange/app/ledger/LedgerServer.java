package com.exchange.app.ledger;

import com.exchange.common.db.handler.AutofillMetaObjectHandler;
import com.exchange.common.kafka.config.CustomKafkaConfig;
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
        },
        scanBasePackageClasses = {AutofillMetaObjectHandler.class})
public class LedgerServer {
    public static void main(String[] args) {
        SpringApplication.run(LedgerServer.class, args);
    }
}
