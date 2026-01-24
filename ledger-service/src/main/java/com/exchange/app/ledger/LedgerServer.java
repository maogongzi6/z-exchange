package com.exchange.app.ledger;

import com.exchange.common.db.handler.AutofillMetaObjectHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScans({
        @MapperScan("com.exchange.app.ledger.dao.mapper"),
        @MapperScan("com.exchange.common.outbox.dao.mapper")
})
@SpringBootApplication(
        scanBasePackages = {
                "com.exchange.app.ledger",
                "com.exchange.common.outbox.dao"
        },
        scanBasePackageClasses = {AutofillMetaObjectHandler.class})
public class LedgerServer {
    public static void main(String[] args) {
        SpringApplication.run(LedgerServer.class, args);
    }
}
