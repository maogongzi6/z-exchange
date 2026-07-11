package com.exchange.app.ledger.kafka.config;

import com.exchange.app.ledger.kafka.consumer.LedgerCommandMessageHandler;
import com.exchange.app.ledger.kafka.consumer.LedgerFailureReplyFactory;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.common.kafka.listener.ListenerRetryPolicy;
import com.exchange.common.kafka.listener.handler.MessageHandler;
import com.exchange.common.kafka.listener.handler.OutboxReplyDefaultMessageHandler;
import com.exchange.common.kafka.listener.retry.KafkaHeaderListenerAttemptResolver;
import com.exchange.common.kafka.listener.retry.ListenerAttemptResolver;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LedgerListenerRetryProperties.class)
public class LedgerListenerConfig {
    @Bean
    public ListenerAttemptResolver listenerAttemptResolver() {
        return new KafkaHeaderListenerAttemptResolver();
    }

    @Bean
    public MessageHandler ledgerMessageHandler(
            LedgerCommandMessageHandler commandMessageHandler,
            LedgerFailureReplyFactory failureReplyFactory
    ) {
        return new OutboxReplyDefaultMessageHandler(commandMessageHandler, failureReplyFactory);
    }

    @Bean
    public com.exchange.common.kafka.listener.DefaultListener ledgerCommonListener(
            MessageHandler ledgerMessageHandler,
            OutboxRepository outboxRepository,
            IPublisher replyWalletPublisher,
            ListenerAttemptResolver listenerAttemptResolver,
            LedgerListenerRetryProperties retryProperties
    ) {
        retryProperties.validate();
        ListenerRetryPolicy retryPolicy = new ListenerRetryPolicy(
                retryProperties.getBaseAttemptIntervalMs(),
                retryProperties.getReplyFailureAfterAttempts(),
                retryProperties.getDlqAfterAttempts()
        );
        return new com.exchange.common.kafka.listener.DefaultListener(
                ledgerMessageHandler,
                outboxRepository,
                replyWalletPublisher,
                listenerAttemptResolver,
                retryPolicy,
                LedgerServiceErrorCode.SERVER_ERROR
        );
    }
}
