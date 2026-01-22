package com.exchange.upstream.wallet;

import com.exchange.upstream.kafka.MessagingService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class KafkaTest {
    @Autowired
    MessagingService messagingService;
    @Test
    public void sendMessage() throws IOException {
        messagingService.sendRegistrationMessage("java-test");
    }
}
