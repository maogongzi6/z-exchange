package com.exchange.app.ledger.kafka.config;

import com.exchange.app.ledger.utils.OutboxHelper;
import com.exchange.common.kafka.producer.DefaultPublisher;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.kafka.producer.OutboxEventTypeResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class LedgerPublisherConfig {
    @Bean
    public OutboxEventTypeResolver ledgerOutboxEventTypeResolver() {
        return OutboxHelper::resolveEventType;
    }

    @Bean
    public IPublisher ledgerPublisher(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            @Qualifier("ledgerOutboxEventTypeResolver") OutboxEventTypeResolver eventTypeResolver
    ) {
        return new DefaultPublisher(kafkaTemplate, eventTypeResolver);
    }
}
