package com.exchange.app.ledger.constant.cache;

import com.exchange.app.ledger.config.CustomCacheConfig;
import com.exchange.common.utils.TtlStrategy;
import lombok.Getter;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Getter
@Component
public class CacheTtlStrategies {
    final private CustomCacheConfig.Data dataConfig;

    private TtlStrategy ledgerTxnStrategy;
    private TtlStrategy ledgerRefStrategy;
    private TtlStrategy tombstoneStrategy;

    CacheTtlStrategies(CustomCacheConfig.Data dataConfig) {
        this.dataConfig = dataConfig;
    }

    @PostConstruct
    public void init() {
        ledgerTxnStrategy = new TtlStrategy(dataConfig.getDataCacheTtl(), dataConfig.getJitter());
        ledgerRefStrategy = new TtlStrategy(dataConfig.getIndexCacheTtl(), dataConfig.getJitter());
        tombstoneStrategy = new TtlStrategy(dataConfig.getTombstoneTtl(), dataConfig.getJitter());
    }
}
