package com.exchange.app.ledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;

@Data
@Validated
@ConfigurationProperties(prefix = "app.db.query")
public class DbQueryProperties {
    @Min(1)
    private int defaultBatchSize;
}
