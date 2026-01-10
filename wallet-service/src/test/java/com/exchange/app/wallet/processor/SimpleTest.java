package com.exchange.app.wallet.processor;

import com.exchange.app.wallet.exception.DbException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@Slf4j

public class SimpleTest {
    @Test
    public void test() {
        try {
            throw new DbException(new RuntimeException("okok"));
        } catch (Exception e) {
            log.error("e: ", e);
        }
    }
}
