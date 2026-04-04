package com.exchange.app.wallet.constant.cache;

import com.exchange.app.wallet.config.CustomCacheProperties;
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

    private TtlStrategy balanceSnapshotStrategy;
    private TtlStrategy balanceSnapshotRefStrategy;
    private TtlStrategy negativeStrategy;

    CacheTtlStrategies(CustomCacheProperties.Data dataConfig) {
        this.dataConfig = dataConfig;
    }

    @PostConstruct
    public void init() {
        balanceSnapshotStrategy = new TtlStrategy(dataConfig.getEntityCacheTtl(), dataConfig.getJitter());
        balanceSnapshotRefStrategy = new TtlStrategy(dataConfig.getIndexCacheTtl(), dataConfig.getJitter());
        negativeStrategy = new TtlStrategy(dataConfig.getNegativeTtl(), dataConfig.getJitter());
    }
}
