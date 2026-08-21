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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Recovers pending outbox events through bounded, page-oriented publish attempts.
 *
 * <p>A short database transaction claims each page by advancing its next-attempt time. All sends
 * in the page are started before waiting for results, allowing broker acknowledgments to arrive
 * concurrently. Only acknowledged sends are finalized as {@link OutboxStatus#SENT}; failed or
 * timed-out sends remain pending for a later retry cycle.</p>
 *
 * <p>Delivery is at-least-once: if Kafka accepts an event but database finalization fails, the row
 * is published again later. Consumers therefore must process event identifiers idempotently.</p>
 */
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
            // Start the whole page before joining so broker acknowledgment waits overlap.
            List<CompletableFuture<PublishAttempt>> publishFutures = outboxes.stream()
                    .map(this::publishAsync)
                    .toList();
            for (CompletableFuture<PublishAttempt> publishFuture : publishFutures) {
                PublishAttempt attempt = publishFuture.join();
                if (attempt.success()) {
                    successOutboxes.add(attempt.outbox());
                } else {
                    failedOutboxes.add(attempt.outbox());
                }
            }
            // Finalize only broker-acknowledged attempts; every other row remains retryable.
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

    private CompletableFuture<PublishAttempt> publishAsync(Outbox outbox) {
        try {
            CompletableFuture<Result<Void>> publishFuture = publisher.publishAsync(outbox);
            if (publishFuture == null) {
                log.error("outbox publish returned null future, eventId={}, commandId={}", outbox.getEventId(), outbox.getCommandId());
                return CompletableFuture.completedFuture(PublishAttempt.failed(outbox));
            }
            return publishFuture.handle((result, throwable) -> {
                if (throwable != null) {
                    log.error(
                            "outbox publish completed exceptionally, eventId={}, commandId={}",
                            outbox.getEventId(),
                            outbox.getCommandId(),
                            throwable
                    );
                    return PublishAttempt.failed(outbox);
                }
                return result != null && result.isSuccess()
                        ? PublishAttempt.succeeded(outbox)
                        : PublishAttempt.failed(outbox);
            });
        } catch (Exception e) {
            log.error(
                    "outbox publish setup threw exception, eventId={}, commandId={}",
                    outbox.getEventId(),
                    outbox.getCommandId(),
                    e
            );
            return CompletableFuture.completedFuture(PublishAttempt.failed(outbox));
        }
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

    private record PublishAttempt(Outbox outbox, boolean success) {
        private static PublishAttempt succeeded(Outbox outbox) {
            return new PublishAttempt(outbox, true);
        }

        private static PublishAttempt failed(Outbox outbox) {
            return new PublishAttempt(outbox, false);
        }
    }
}
