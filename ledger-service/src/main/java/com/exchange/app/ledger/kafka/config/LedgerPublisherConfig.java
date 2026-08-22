package com.exchange.app.ledger.kafka.config;

import com.exchange.app.ledger.utils.OutboxHelper;
import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.kafka.producer.DefaultPublisher;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.kafka.producer.OutboxEventTypeResolver;
import com.exchange.common.kafka.producer.metrics.MeteredPublisher;
import com.exchange.common.outbox.config.OutboxProperties;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.retry.OutboxRetryHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class LedgerPublisherConfig {
    @Bean
    public OutboxEventTypeResolver ledgerOutboxEventTypeResolver() {
        return OutboxHelper::resolveEventType;
    }

    @Bean
    public IPublisher ledgerPublisher(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            @Qualifier("ledgerOutboxEventTypeResolver") OutboxEventTypeResolver eventTypeResolver,
            MeterRegistry meterRegistry
    ) {
        return new MeteredPublisher(
                new DefaultPublisher(kafkaTemplate, eventTypeResolver),
                eventTypeResolver,
                meterRegistry
        );
    }

    @Bean
    public OutboxRetryHandler ledgerOutboxRetryHandler(
            OutboxProperties outboxProperties,
            OutboxRepository outboxRepository,
            DbTxnExecutor dbTxnExecutor,
            @Qualifier("ledgerPublisher") IPublisher publisher
    ) {
        return new OutboxRetryHandler(outboxProperties, outboxRepository, dbTxnExecutor, publisher);
    }
}
