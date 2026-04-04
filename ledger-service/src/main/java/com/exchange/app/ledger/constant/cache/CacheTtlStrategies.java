package com.exchange.app.ledger.constant.cache;

import com.exchange.app.ledger.config.CustomCacheProperties;
import com.exchange.common.redis.cache.model.StrategyOption;
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

    private StrategyOption.TtlOption ledgerTxnOption;
    private TtlStrategy defaultEntityStrategy;
    private TtlStrategy tombstoneStrategy;
    private TtlStrategy negativeStrategy;

    private TtlStrategy ledgerRefStrategy;


    CacheTtlStrategies(CustomCacheProperties.Data dataConfig) {
        this.dataConfig = dataConfig;
    }

    @PostConstruct
    public void init() {
        ledgerRefStrategy = new TtlStrategy(dataConfig.getIndexCacheTtl(), dataConfig.getJitter());
        tombstoneStrategy = new TtlStrategy(dataConfig.getTombstoneTtl(), dataConfig.getJitter());
        negativeStrategy = new TtlStrategy(dataConfig.getNegativeTtl(), dataConfig.getJitter());
        defaultEntityStrategy = new TtlStrategy(dataConfig.getEntityCacheTtl(), dataConfig.getJitter());

        ledgerTxnOption = new StrategyOption.TtlOption(defaultEntityStrategy, negativeStrategy, tombstoneStrategy);
    }
}
