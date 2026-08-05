package com.exchange.app.wallet.dao.cache;

import com.exchange.app.wallet.constant.cache.CacheTtlConfig;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.codec.impl.VersionCodec;
import com.exchange.common.redis.cache.component.support.DefaultCacheReader;
import com.exchange.common.redis.cache.component.support.RawCacheDeleter;
import com.exchange.common.redis.cache.component.support.RawCacheWriter;
import com.exchange.common.redis.cache.component.support.VersionCacheWriter;
import com.exchange.common.redis.cache.strategy.RawCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.redis.cache.strategy.impl.StrategyFactory;
import com.exchange.common.redis.cache.strategy.metrics.MetricRawCacheStrategy;
import com.exchange.common.redis.cache.strategy.metrics.MetricVersionCacheStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class BalanceSnapshotCacheRegisterTest {
    @SuppressWarnings("unchecked")
    @Test
    void decoratesAllFourStrategiesWithoutChangingTheirInterfaces() {
        // The register retains the raw/version contracts consumed by BalanceSnapshotStore while
        // inserting the common metric decorator as the outermost layer.
        BalanceSnapshotCacheRegister register = new BalanceSnapshotCacheRegister();
        DefaultCacheReader<ValueCodec> rawReader = mock(DefaultCacheReader.class);
        DefaultCacheReader<VersionCodec> versionReader = mock(DefaultCacheReader.class);
        RawCacheWriter rawWriter = mock(RawCacheWriter.class);
        VersionCacheWriter versionWriter = mock(VersionCacheWriter.class);
        RawCacheDeleter deleter = mock(RawCacheDeleter.class);
        CacheTtlConfig ttlConfig = mock(CacheTtlConfig.class);
        StrategyFactory factory = new StrategyFactory();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        RawCacheStrategy<String> normalRef = register.balanceRefCache(
                rawReader, rawWriter, deleter, factory, ttlConfig, registry);
        RawCacheStrategy<String> hotRef = register.hotBalanceRefCache(
                rawReader, rawWriter, deleter, factory, ttlConfig, registry);
        VersionCacheStrategy<BalanceSnapshot> normalSnapshot = register.normalBalanceCache(
                versionReader, versionWriter, deleter, factory, ttlConfig, registry);
        VersionCacheStrategy<BalanceSnapshot> hotSnapshot = register.hotBalanceCache(
                versionReader, versionWriter, deleter, factory, ttlConfig, registry);

        assertInstanceOf(MetricRawCacheStrategy.class, normalRef);
        assertInstanceOf(MetricRawCacheStrategy.class, hotRef);
        assertInstanceOf(MetricVersionCacheStrategy.class, normalSnapshot);
        assertInstanceOf(MetricVersionCacheStrategy.class, hotSnapshot);
    }
}
