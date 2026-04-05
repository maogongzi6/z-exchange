package com.exchange.app.ledger.constant.cache;

import com.exchange.app.ledger.config.CustomCacheProperties;
import com.exchange.common.utils.TtlStrategy;
import lombok.Getter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Getter
@Component
@EnableConfigurationProperties(CustomCacheProperties.Data.class)
public class CacheTtlConfig {
    final private CustomCacheProperties.Data dataConfig;

    private TtlStrategy defaultEntityStrategy;
    private TtlStrategy defaultRefStrategy;
    private TtlStrategy tombstoneStrategy;
    private TtlStrategy negativeStrategy;

    CacheTtlConfig(CustomCacheProperties.Data dataConfig) {
        this.dataConfig = dataConfig;
    }

    @PostConstruct
    public void init() {
        defaultRefStrategy = new TtlStrategy(dataConfig.getIndexCacheTtl(), dataConfig.getJitter());
        defaultEntityStrategy = new TtlStrategy(dataConfig.getEntityCacheTtl(), dataConfig.getJitter());
        tombstoneStrategy = new TtlStrategy(dataConfig.getTombstoneTtl(), dataConfig.getJitter());
        negativeStrategy = new TtlStrategy(dataConfig.getNegativeTtl(), dataConfig.getJitter());
    }
}
