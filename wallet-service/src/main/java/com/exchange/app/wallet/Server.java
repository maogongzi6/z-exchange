package com.exchange.app.wallet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.exchange.app.wallet.dao.mapper")
@SpringBootApplication
public class Server {
    public static void main(String[] args) {
        SpringApplication.run(Server.class, args);
    }
}
