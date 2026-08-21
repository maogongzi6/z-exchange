package com.exchange.app.wallet.kafka.config;

import com.exchange.app.wallet.utils.OutboxHelper;
import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.kafka.producer.DefaultPublisher;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.kafka.producer.OutboxEventTypeResolver;
import com.exchange.common.outbox.config.OutboxProperties;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.retry.OutboxRetryHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
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

    @Bean
    public OutboxRetryHandler walletOutboxRetryHandler(
            OutboxProperties outboxProperties,
            OutboxRepository outboxRepository,
            DbTxnExecutor dbTxnExecutor,
            @Qualifier("walletPublisher") IPublisher publisher
    ) {
        return new OutboxRetryHandler(outboxProperties, outboxRepository, dbTxnExecutor, publisher);
    }
}
