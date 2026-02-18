package com.exchange.common.outbox.retry;

import com.exchange.common.db.utils.DbTransactionHelper;
import com.exchange.common.kafka.producer.IPublisher;
import com.exchange.common.outbox.config.OutboxConfig;
import com.exchange.common.outbox.dao.repository.OutboxRepository;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class OutboxRetryHandler {
    private final OutboxConfig outboxConfig;
    private final OutboxRepository outboxManager;
    private final TransactionTemplate transactionTemplate;
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
            Boolean ifSuccess = DbTransactionHelper.executeWithIfSuccess(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
                List<Outbox> locked = outboxManager.selectForClaimSkipLock(now, outboxConfig.getMaxRetries(), lastId.get(), outboxConfig.getPageLimit());
                outboxes.addAll(locked);
                if (outboxes.isEmpty()) {
                    return true;
                }
                lastId.set(locked.get(locked.size() - 1).getId());
                int claimedCount = outboxManager.batchClaim(locked.stream().map(Outbox::getId).collect(Collectors.toList()), now.plusSeconds(outboxConfig.getAttemptIntervalSec()));
                if (claimedCount != locked.size()) {
                    log.error("unexpected claim outbox failure, claimed: {}, locked: {}", claimedCount, locked);
                    // maybe use Result + CommonErrorCode
                    return false;
                }
                return true;
            });
            if (!ifSuccess) {
                log.error("claim failed, outbox: {}", outboxes);
                continue;
            }
            // retry publish
            for (Outbox outbox : outboxes) {
                Result<Void> publishResult = publisher.publish(outbox);
                if (publishResult.success) {
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
