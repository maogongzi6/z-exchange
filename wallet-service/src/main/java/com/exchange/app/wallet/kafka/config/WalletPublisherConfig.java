package com.exchange.app.wallet.kafka.config;

import com.exchange.app.wallet.utils.OutboxHelper;
import com.exchange.common.kafka.producer.DefaultPublisher;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.kafka.producer.OutboxEventTypeResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class WalletPublisherConfig {
    @Bean
    public OutboxEventTypeResolver walletOutboxEventTypeResolver() {
        return OutboxHelper::resolveEventType;
    }

    @Bean
    public IPublisher walletPublisher(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            @Qualifier("walletOutboxEventTypeResolver") OutboxEventTypeResolver eventTypeResolver
    ) {
        return new DefaultPublisher(kafkaTemplate, eventTypeResolver);
    }
}
