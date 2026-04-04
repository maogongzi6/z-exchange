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
public class CacheTtlStrategies {
    final private CustomCacheProperties.Data dataConfig;

    private TtlStrategy ledgerTxnStrategy;
    private TtlStrategy ledgerRefStrategy;
    private TtlStrategy tombstoneStrategy;
    private TtlStrategy negativeStrategy;

    CacheTtlStrategies(CustomCacheProperties.Data dataConfig) {
        this.dataConfig = dataConfig;
    }

    @PostConstruct
    public void init() {
        ledgerTxnStrategy = new TtlStrategy(dataConfig.getEntityCacheTtl(), dataConfig.getJitter());
        ledgerRefStrategy = new TtlStrategy(dataConfig.getIndexCacheTtl(), dataConfig.getJitter());
        tombstoneStrategy = new TtlStrategy(dataConfig.getTombstoneTtl(), dataConfig.getJitter());
        negativeStrategy = new TtlStrategy(dataConfig.getNegativeTtl(), dataConfig.getJitter());
    }
}
