package com.exchange.common.kafka.container;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

@Configuration
public class CommandContainerRegister {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> concurrentCommandKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        var factory = createCommandFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> concurrentRetryTopicCommandKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> consumerFactory
    ) {
        return createCommandFactory(consumerFactory);
    }

    private ConcurrentKafkaListenerContainerFactory<String, byte[]> createCommandFactory(
            ConsumerFactory<String, byte[]> consumerFactory
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        ContainerProperties containerProperties = factory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL);
        containerProperties.setDeliveryAttemptHeader(true);
        return factory;
    }
}
