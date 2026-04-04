package com.exchange.app.wallet.constant.cache;

import com.exchange.app.wallet.config.CustomCacheProperties;
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

    private TtlStrategy defaultEntityStrategy;
    private TtlStrategy negativeStrategy;
    private TtlStrategy tombstoneStrategy;

    private TtlStrategy balanceSnapshotRefStrategy;

    private StrategyOption.TtlOption hotLedgerTxnOption;
    private StrategyOption.TtlOption normalLedgerTxnOption;

    CacheTtlStrategies(CustomCacheProperties.Data dataConfig) {
        this.dataConfig = dataConfig;
    }

    @PostConstruct
    public void init() {
        balanceSnapshotRefStrategy = new TtlStrategy(dataConfig.getIndexCacheTtl(), dataConfig.getJitter());

        defaultEntityStrategy = new TtlStrategy(dataConfig.getEntityCacheTtl(), dataConfig.getJitter());
        negativeStrategy = new TtlStrategy(dataConfig.getNegativeTtl(), dataConfig.getJitter());
        tombstoneStrategy = new TtlStrategy(dataConfig.getTombstoneTtl(), dataConfig.getJitter());

        hotLedgerTxnOption = new StrategyOption.TtlOption(defaultEntityStrategy, negativeStrategy, tombstoneStrategy);
        normalLedgerTxnOption = new StrategyOption.TtlOption(defaultEntityStrategy, negativeStrategy, tombstoneStrategy);
    }
}
