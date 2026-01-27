package com.exchange.common.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.kafka")
public class CustomKafkaConfig {
    private Class<? extends Exception>[] notRetriableExceptions;
}
