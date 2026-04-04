package com.exchange.common.outbox.retry;

import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.config.OutboxProperties;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
@EnableConfigurationProperties(OutboxProperties.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class OutboxRetryHandler {
    private final OutboxProperties outboxConfig;
    private final OutboxRepository outboxManager;
    private final DbTxnExecutor dbTxnExecutor;
    private final IPublisher publisher;


    // TODO maybe add a execute time limit
    public void retry() {
        LocalDateTime now = LocalDateTime.now();
        AtomicLong lastId = new AtomicLong();
        int successCount = 0, failedCount = 0;
        while (true) {
            final List<Outbox> outboxes = new ArrayList<>();
            List<Outbox> successOutboxes = new ArrayList<>(), failedOutboxes = new ArrayList<>();
            // select for update skip lock + update next_attempt_at to claim
            Result<Void> result = dbTxnExecutor.executeWithDefault(() -> {
                List<Outbox> locked = outboxManager.selectForClaimSkipLock(now, outboxConfig.getMaxRetries(), lastId.get(), outboxConfig.getPageLimit());
                outboxes.addAll(locked);
                if (outboxes.isEmpty()) {
                    return Results.success();
                }
                lastId.set(locked.get(locked.size() - 1).getId());
                int claimedCount = outboxManager.batchClaim(locked.stream().map(Outbox::getId).collect(Collectors.toList()), now.plusSeconds(outboxConfig.getAttemptIntervalSec()));
                if (claimedCount != locked.size()) {
                    log.error("unexpected claim outbox failure, claimed: {}, locked: {}", claimedCount, locked);
                    // maybe use Result + CommonErrorCode
                    return Results.fail(CommonErrorCode.UNEXPECTED_DB_ERROR, "unexpected claim outbox failure");
                }
                return Results.success();
            });
            if (!result.success()) {
                log.error("claim failed, outbox: {}", outboxes);
                continue;
            }
            // retry publish
            for (Outbox outbox : outboxes) {
                Result<Void> publishResult = publisher.publish(outbox);
                if (publishResult.success()) {
                    successOutboxes.add(outbox);
                } else {
                    failedOutboxes.add(outbox);
                }
            }
            // update to SENT
            if (!successOutboxes.isEmpty()) {
                int updatedCount = outboxManager.batchUpdateStatusToFinalize(successOutboxes.stream().map(Outbox::getId).collect(Collectors.toList()), OutboxStatus.PENDING, OutboxStatus.SENT, now);
                if (updatedCount != successOutboxes.size()) {
                    log.error("fail to finalize success outboxes, updated count: {}, need update size: {}, outboxes: {}", updatedCount, successOutboxes.size(), successOutboxes);
                }
                successCount += updatedCount;
                failedCount += successOutboxes.size() - updatedCount;
            }
            if (!failedOutboxes.isEmpty()) {
                log.error("fail to publish outboxes: {}", failedOutboxes);
                failedCount += failedOutboxes.size();
            }
            if (outboxes.size() < outboxConfig.getPageLimit()) {
                break;
            }
        }
        log.info("outbox retry success: {}, failed: {}", successCount, failedCount);
    }
}
