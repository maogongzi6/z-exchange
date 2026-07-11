package com.exchange.common.kafka.retry;

import com.exchange.common.exception.RetriableException;
import com.exchange.common.kafka.config.CustomKafkaProperties;
import com.exchange.common.kafka.listener.exception.DlqException;
import com.exchange.common.kafka.listener.exception.KafkaListenerRetriableException;
import com.exchange.common.kafka.utils.Topics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

@Slf4j
@Configuration
@EnableConfigurationProperties(CustomKafkaProperties.class)
public class KafkaErrorHandlingRegister {
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate, (record, e) -> {
            String dlqTopic = Topics.dlq(record.topic());
            log.error("dlq message, topic:{} , key: {}, value: {}", record.topic(), record.key(), record.value());
            return new TopicPartition(dlqTopic, record.partition());
        });
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer deadLetterPublishingRecoverer, CustomKafkaProperties customKafkaConfig) {
        long[] delays = new long[] {
                1_000, 1_000, 1_000, 1_000, 1_000, 1_000,     // 1 initial delivery + 6 retries
        };
        StairStepBackOff backOff = new StairStepBackOff(delays, delays.length);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
        errorHandler.defaultFalse();
        errorHandler.addRetryableExceptions(KafkaListenerRetriableException.class, RetriableException.class);
        errorHandler.addNotRetryableExceptions(DlqException.class);
        Class<? extends Exception>[] notRetriableExceptions = customKafkaConfig.getNotRetriableExceptions();
        if (notRetriableExceptions != null && notRetriableExceptions.length > 0) {
            errorHandler.addNotRetryableExceptions(notRetriableExceptions);
        }
        errorHandler.setRetryListeners(((consumerRecord, e, i) -> {
            log.error("error_handler: retry record, attempt_count = {}, record = {}, exception = {}", i, consumerRecord.key(), e.getMessage());
        }));
        return errorHandler;
    }
}
