package com.exchange.app.ledger.cronjob.outbox;

import com.exchange.common.outbox.retry.OutboxRetryHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class OutboxDispatcher {
    private final OutboxRetryHandler retryHandler;

    @Scheduled(cron = "${app.outbox.interval}")
    public void dispatch() {
        log.info("outbox dispatch start");
        retryHandler.retry();
        log.info("outbox dispatch end");
    }
}
