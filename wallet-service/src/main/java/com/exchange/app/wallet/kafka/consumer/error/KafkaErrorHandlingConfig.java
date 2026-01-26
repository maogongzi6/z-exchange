package com.exchange.app.wallet.kafka.consumer.error;

import com.exchange.app.wallet.exception.AbnormalProtoDataException;
import com.exchange.common.kafka.utils.Topics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate, (record, e) -> {
            String dlqTopic = Topics.dlq(record.topic());
            log.error("dlq message, topic:{} , key: {}, value: {}", record.topic(), record.key(), record.value());
            return new TopicPartition(dlqTopic, record.partition());
        });
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        long[] delays = new long[] {
                1_000, 1_000, 1_000,     // 3x 1s
        };
        StairStepBackOff backOff = new StairStepBackOff(delays, delays.length);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(AbnormalProtoDataException.class);
        errorHandler.setRetryListeners(((consumerRecord, e, i) -> {
            log.error("error_handler: retry record, attempt_count = {}, record = {}, exception = {}", i, consumerRecord.key(), e.getMessage());
        }));
        return errorHandler;
    }
}
