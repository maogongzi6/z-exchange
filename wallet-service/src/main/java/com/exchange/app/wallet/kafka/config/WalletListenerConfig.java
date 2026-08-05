package com.exchange.app.wallet.kafka.config;

import com.exchange.app.wallet.kafka.consumer.WalletLedgerReplyMessageHandler;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.kafka.listener.ListenerRetryPolicy;
import com.exchange.common.kafka.listener.action.ListenerAction;
import com.exchange.common.kafka.listener.exception.DlqException;
import com.exchange.common.kafka.listener.failure.ListenerFailure;
import com.exchange.common.kafka.listener.handler.AckOnlyDefaultMessageHandler;
import com.exchange.common.kafka.listener.handler.MessageHandler;
import com.exchange.common.kafka.listener.retry.KafkaHeaderListenerAttemptResolver;
import com.exchange.common.kafka.listener.retry.ListenerAttemptResolver;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.proto.common.event.EventEnvelopePb;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WalletListenerRetryProperties.class)
public class WalletListenerConfig {
    @Bean
    public ListenerAttemptResolver walletListenerAttemptResolver() {
        return new KafkaHeaderListenerAttemptResolver();
    }

    @Bean
    public MessageHandler walletAckOnlyMessageHandler(WalletLedgerReplyMessageHandler ledgerReplyHandler) {
        /*
         * Wallet is the terminal consumer of the ledger reply and does not create another reply
         * outbox. An explicit failure policy is required because AckOnly's convenience default
         * acknowledges failures, which would silently discard an unapplied ledger result.
         */
        return new AckOnlyDefaultMessageHandler(ledgerReplyHandler, this::routeFailureToDlt);
    }

    @Bean
    public com.exchange.common.kafka.listener.DefaultListener walletCommonListener(
            @Qualifier("walletAckOnlyMessageHandler") MessageHandler messageHandler,
            OutboxRepository outboxRepository,
            IPublisher publisher,
            @Qualifier("walletListenerAttemptResolver") ListenerAttemptResolver attemptResolver,
            WalletListenerRetryProperties retryProperties
    ) {
        retryProperties.validate();
        /*
         * AckOnly has no failed reply to materialize. Align the common threshold with the final
         * RetryableTopic attempt so recoverable failures remain non-blocking retries until Spring
         * performs the final DLT transition.
         */
        ListenerRetryPolicy retryPolicy = new ListenerRetryPolicy(0L, retryProperties.getAttempts());
        return new com.exchange.common.kafka.listener.DefaultListener(
                messageHandler,
                outboxRepository,
                publisher,
                attemptResolver,
                retryPolicy,
                WalletServiceErrorCode.SERVER_ERROR
        );
    }

    private ListenerAction routeFailureToDlt(EventEnvelopePb envelope, ListenerFailure failure) {
        String detail = "wallet ledger-reply listener failure, eventId=" + envelope.getEventId()
                + ", type=" + failure.type()
                + ", detail=" + failure.detail();
        throw new DlqException(failure.errorCode(), detail, failure.cause());
    }
}
