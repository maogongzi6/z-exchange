package com.exchange.app.wallet.register;

import com.exchange.common.component.DefaultPromotionClassifier;
import com.exchange.common.component.PromotionClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromotionClassifierRegister {
    @Bean("hotBalanceSnapshotClassifier")
    public PromotionClassifier hotBalanceSnapshotClassifier() {
        return new DefaultPromotionClassifier();
    }
}
