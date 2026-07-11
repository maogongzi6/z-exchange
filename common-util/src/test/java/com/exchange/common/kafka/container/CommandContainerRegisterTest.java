package com.exchange.common.kafka.container;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CommandContainerRegisterTest {
    @Test
    void retryTopicCommandFactoryUsesManualAckAndDeliveryAttemptHeadersWithoutLegacyErrorHandlerDependency() {
        // KL-CFG-006: RetryableTopic installs its own retry/DLT handling, so this factory is plain.
        CommandContainerRegister register = new CommandContainerRegister();
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, byte[]> consumerFactory = mock(ConsumerFactory.class);

        var factory = register.concurrentRetryTopicCommandKafkaListenerContainerFactory(consumerFactory);

        ContainerProperties properties = factory.getContainerProperties();
        assertEquals(ContainerProperties.AckMode.MANUAL, properties.getAckMode());
        assertTrue(properties.isDeliveryAttemptHeader());
    }
}
