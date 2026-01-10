package com.exchange.app.ledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.exchange.app.ledger.dao.mapper")
@SpringBootApplication
public class LedgerServer {
    public static void main(String[] args) {
        SpringApplication.run(LedgerServer.class, args);
    }
}
