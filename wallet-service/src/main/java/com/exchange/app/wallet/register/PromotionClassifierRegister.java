package com.exchange.app.wallet.register;

import com.exchange.common.component.DefaultPromotionClassifier;
import com.exchange.common.component.PromotionClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromotionClassifierRegister {
    @Bean("hotBalanceClassifier")
    public PromotionClassifier hotBalanceClassifier() {
        return new DefaultPromotionClassifier();
    }

    @Bean("hotBalanceRefClassifier")
    public PromotionClassifier hotBalanceRefClassifier() {
        return new DefaultPromotionClassifier();
    }
}
