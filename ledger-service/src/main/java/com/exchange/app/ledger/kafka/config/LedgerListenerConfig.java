package com.exchange.app.ledger.kafka.config;

import com.exchange.app.ledger.kafka.consumer.LedgerCommandMessageHandler;
import com.exchange.app.ledger.kafka.consumer.LedgerFailureReplyFactory;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.common.kafka.listener.ListenerRetryPolicy;
import com.exchange.common.kafka.listener.handler.MessageHandler;
import com.exchange.common.kafka.listener.handler.OutboxReplyDefaultMessageHandler;
import com.exchange.common.kafka.listener.metrics.KafkaListenerMetrics;
import com.exchange.common.kafka.listener.metrics.KafkaListenerDltMetrics;
import com.exchange.common.kafka.listener.metrics.MicrometerKafkaListenerMetrics;
import com.exchange.common.kafka.listener.retry.KafkaHeaderListenerAttemptResolver;
import com.exchange.common.kafka.listener.retry.ListenerAttemptResolver;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

import static com.exchange.common.kafka.container.CommandContainerRegister.OUTBOX_PUBLISH_CALLBACK_EXECUTOR;

@Configuration
@EnableConfigurationProperties(LedgerListenerRetryProperties.class)
public class LedgerListenerConfig {
    private static final String LEDGER_WALLET_POST_LISTENER = "wallet_post";

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
    public KafkaListenerMetrics ledgerKafkaListenerMetrics(MeterRegistry meterRegistry) {
        return new MicrometerKafkaListenerMetrics(meterRegistry, LEDGER_WALLET_POST_LISTENER);
    }

    @Bean
    public KafkaListenerDltMetrics ledgerKafkaListenerDltMetrics(MeterRegistry meterRegistry) {
        return new KafkaListenerDltMetrics(meterRegistry, LEDGER_WALLET_POST_LISTENER);
    }

    @Bean
    public com.exchange.common.kafka.listener.DefaultListener ledgerCommonListener(
            MessageHandler ledgerMessageHandler,
            OutboxRepository outboxRepository,
            IPublisher replyWalletPublisher,
            ListenerAttemptResolver listenerAttemptResolver,
            LedgerListenerRetryProperties retryProperties,
            @Qualifier(OUTBOX_PUBLISH_CALLBACK_EXECUTOR) Executor publishCallbackExecutor,
            @Qualifier("ledgerKafkaListenerMetrics") KafkaListenerMetrics listenerMetrics
    ) {
        retryProperties.validate();
        ListenerRetryPolicy retryPolicy = new ListenerRetryPolicy(
                retryProperties.getBaseAttemptIntervalMs(),
                retryProperties.getReplyFailureAfterAttempts()
        );
        return new com.exchange.common.kafka.listener.DefaultListener(
                ledgerMessageHandler,
                outboxRepository,
                replyWalletPublisher,
                listenerAttemptResolver,
                retryPolicy,
                LedgerServiceErrorCode.SERVER_ERROR,
                publishCallbackExecutor,
                listenerMetrics
        );
    }
}
