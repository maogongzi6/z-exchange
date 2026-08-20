package com.exchange.common.outbox.retry;

import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.config.OutboxProperties;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.OutboxErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        long lastId = 0L;
        int successCount = 0, failedCount = 0;
        while (true) {
            Result<List<Outbox>> result = claimPage(now, lastId);
            if (!result.isSuccess()) {
                log.error("claim failed, stopping current retry cycle, lastId: {}, result: {}", lastId, result);
                break;
            }
            List<Outbox> outboxes = result.getValue();
            if (!outboxes.isEmpty()) {
                lastId = outboxes.getLast().getId();
            }

            List<Outbox> successOutboxes = new ArrayList<>(), failedOutboxes = new ArrayList<>();
            // retry publish
            for (Outbox outbox : outboxes) {
                Result<Void> publishResult = publisher.publish(outbox);
                if (publishResult.isSuccess()) {
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

    private Result<List<Outbox>> claimPage(LocalDateTime now, long lastId) {
        // select for update skip lock + update next_attempt_at to claim
        return dbTxnExecutor.executeWithDefault(() -> {
            List<Outbox> locked = outboxManager.selectForClaimSkipLock(now, outboxConfig.getMaxRetries(), lastId, outboxConfig.getPageLimit());
            if (locked.isEmpty()) {
                return Result.success(List.of());
            }
            int claimedCount = outboxManager.batchClaim(locked.stream().map(Outbox::getId).collect(Collectors.toList()), now.plusSeconds(outboxConfig.getAttemptIntervalSec()));
            if (claimedCount != locked.size()) {
                log.error("unexpected claim outbox failure, claimed: {}, locked: {}", claimedCount, locked);
                return Result.failure(OutboxErrorCode.UNEXPECTED_DB_ERROR, "unexpected claim outbox failure");
            }
            return Result.success(List.copyOf(locked));
        });
    }
}
