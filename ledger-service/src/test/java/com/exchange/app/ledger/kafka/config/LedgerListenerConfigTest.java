package com.exchange.app.ledger.kafka.config;

import com.exchange.common.kafka.listener.DefaultListener;
import com.exchange.common.kafka.listener.ListenerRetryPolicy;
import com.exchange.common.kafka.listener.handler.MessageHandler;
import com.exchange.common.kafka.listener.retry.ListenerAttemptResolver;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class LedgerListenerConfigTest {
    @Test
    void ledgerCommonListenerUsesConfiguredReplyFailureThresholdWithoutListenerDlqThreshold() {
        // KL-CFG-003: RetryableTopic attempts own final DLT routing; listener only owns reply threshold.
        LedgerListenerRetryProperties properties = new LedgerListenerRetryProperties();
        properties.setAttempts(7);
        properties.setReplyFailureAfterAttempts(5);
        properties.setBaseAttemptIntervalMs(1234);

        DefaultListener listener = new LedgerListenerConfig().ledgerCommonListener(
                mock(MessageHandler.class),
                mock(OutboxRepository.class),
                mock(IPublisher.class),
                mock(ListenerAttemptResolver.class),
                properties
        );

        ListenerRetryPolicy retryPolicy = (ListenerRetryPolicy) ReflectionTestUtils.getField(listener, "retryPolicy");
        assertEquals(5, retryPolicy.replyFailureAfterAttempts());
        assertEquals(1234, retryPolicy.baseAttemptIntervalMs());
        assertFalse(Arrays.stream(ListenerRetryPolicy.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("dlqAfterAttempts")));
    }
}
