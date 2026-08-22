package com.exchange.app.ledger.kafka.consumer;

import com.exchange.app.ledger.kafka.constant.LedgerTopic;
import com.exchange.common.kafka.listener.exception.DlqException;
import com.exchange.common.kafka.listener.exception.KafkaListenerRetriableException;
import com.exchange.common.kafka.listener.metrics.KafkaListenerDltMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class DefaultListener {
    private final com.exchange.common.kafka.listener.DefaultListener delegate;
    private final KafkaListenerDltMetrics dltMetrics;

    public DefaultListener(
            @Qualifier("ledgerCommonListener") com.exchange.common.kafka.listener.DefaultListener delegate,
            KafkaListenerDltMetrics dltMetrics
    ) {
        this.delegate = delegate;
        this.dltMetrics = dltMetrics;
    }

    @RetryableTopic(
            attempts = "${app.kafka.listener.retry.attempts:7}",
            backoff = @Backoff(
                    delayExpression = "${app.kafka.listener.retry.backoff-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.listener.retry.backoff-multiplier:1.0}",
                    maxDelayExpression = "${app.kafka.listener.retry.backoff-max-delay-ms:25000}"
            ),
            include = {KafkaListenerRetriableException.class},
            listenerContainerFactory = "concurrentRetryTopicCommandKafkaListenerContainerFactory",
            autoCreateTopics = "${app.kafka.listener.retry.auto-create-topics:false}",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
            topics = LedgerTopic.WALLET_POST,
            groupId = "wallet-post",
            containerFactory = "concurrentRetryTopicCommandKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        delegate.onMessage(record.value(), ack, record.headers(), record.timestamp());
    }

    @DltHandler
    public void onDltMessage(ConsumerRecord<String, byte[]> record) {
        String originalTopic = headerString(record, KafkaHeaders.ORIGINAL_TOPIC, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        /*
         * Record DLT only here, where Spring has actually routed and delivered the record.
         * The common listener can classify a failure as non-retryable but cannot prove DLT delivery.
         */
        dltMetrics.record(originalTopic);
        log.error(
                "ledger listener dlt message, topic={}, partition={}, offset={}, key={}, original_topic={}, exception_class={}, exception_message={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                originalTopic,
                headerString(record, KafkaHeaders.EXCEPTION_FQCN, KafkaHeaders.DLT_EXCEPTION_FQCN),
                headerString(record, KafkaHeaders.EXCEPTION_MESSAGE, KafkaHeaders.DLT_EXCEPTION_MESSAGE)
        );
    }

    private String headerString(ConsumerRecord<String, byte[]> record, String primaryName, String fallbackName) {
        Header header = record.headers().lastHeader(primaryName);
        if (header == null) {
            header = record.headers().lastHeader(fallbackName);
        }
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
