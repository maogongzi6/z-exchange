package com.exchange.app.wallet.dao.cache;

import com.exchange.app.wallet.constant.cache.CacheScope;
import com.exchange.common.redis.cache.component.support.SimpleRedisSupport;
import com.exchange.common.redis.cache.ops.NegativeCacheOps;
import com.exchange.common.redis.cache.ops.ReadOps;
import com.exchange.common.redis.cache.ops.SelfRecoverOps;
import com.exchange.common.redis.cache.ops.SimpleWriteOps;
import com.exchange.common.redis.cache.ops.impl.NegativeCacheOpsImpl;
import com.exchange.common.redis.cache.ops.impl.RawDeleteRecoverOpsImpl;
import com.exchange.common.redis.cache.ops.impl.ReadOpsImpl;
import com.exchange.common.redis.cache.ops.impl.WriteOpsImpl;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.impl.StrategyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletCacheRegister {
    @Bean(name = "balanceSnapshotRefCache")
    public StableCacheStrategy<String> balanceSnapshotRefCache(SimpleRedisSupport simpleRedisSupport) {
        CacheDescriptor<String> descriptor = new CacheDescriptor<>(String.class, CacheScope::balanceSnapshotRefIdKey);
        ReadOps<String> readOps = new ReadOpsImpl<>(simpleRedisSupport, descriptor);
        SimpleWriteOps<String> simpleWriteOps = new WriteOpsImpl<>(simpleRedisSupport, descriptor);
        NegativeCacheOps negativeCacheOps = new NegativeCacheOpsImpl(simpleRedisSupport, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(simpleRedisSupport, descriptor);
        return new StrategyFactory().buildStableCacheStrategy(readOps, simpleWriteOps, negativeCacheOps, selfRecoverOps);
    }
}
