package com.exchange.common.kafka.container;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.Map;

@Configuration
public class CommandContainerRegister {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> concurrentCommandKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> consumerFactory,
            KafkaProperties properties,
            DefaultErrorHandler errorHandler
    ) {
        Map<String, Object> props = properties.buildConsumerProperties();
        // config consumer (kafka properties)
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // config spring kafka listener (spring.kafka.listener)
        var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        ContainerProperties containerProperties = factory.getContainerProperties();
        // config spring consumer properties (spring.kafka.consumer)
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
