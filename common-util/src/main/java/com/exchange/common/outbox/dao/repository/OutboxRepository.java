package com.exchange.common.outbox.dao.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exchange.common.db.manager.DbBaseRepository;
import com.exchange.common.kafka.listener.outbox.OutboxInsertDecision;
import com.exchange.common.outbox.dao.mapper.OutboxMapper;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class OutboxRepository extends DbBaseRepository<Outbox, OutboxMapper> {
    public OutboxRepository(OutboxMapper mapper) {
        super(mapper);
    }

    public int insertWithClaim(Outbox outbox, LocalDateTime nextAttemptAt) {
        outbox.setAttemptCount(1);
        outbox.setNextAttemptAt(nextAttemptAt);
        return insertIgnore(outbox);
    }

    /*
     * INSERT IGNORE only tells us whether this attempt inserted a row. When it does not,
     * verify the existing event id before treating the duplicate as success; same intent is
     * idempotent delivery, missing state is ambiguous, and different intent is a conflict.
     */
    public OutboxInsertDecision insertWithClaimAndVerify(Outbox outbox, LocalDateTime nextAttemptAt) {
        if (insertWithClaim(outbox, nextAttemptAt) == 1) {
            return OutboxInsertDecision.inserted(outbox);
        }

        Outbox existing = selectByEventId(outbox.getEventId());
        if (existing == null) {
            return OutboxInsertDecision.existingMissingAmbiguous(outbox);
        }
        if (sameIntent(outbox, existing)) {
            return OutboxInsertDecision.existingSameIntent(outbox, existing);
        }
        return OutboxInsertDecision.existingConflict(outbox, existing);
    }

    public Outbox selectByEventId(String eventId) {
        var query = Wrappers.<Outbox>lambdaQuery().eq(Outbox::getEventId, eventId);
        return mapper.selectOne(query);
    }

    public int updateStatusToFinalize(Outbox outbox, OutboxStatus newStatus, LocalDateTime finalizedAt) {
        var updateWrapper = new LambdaUpdateWrapper<Outbox>();
        updateWrapper.eq(Outbox::getEventId, outbox.getEventId())
                .eq(Outbox::getOutboxStatus, outbox.getOutboxStatus())
                .set(Outbox::getOutboxStatus, newStatus)
                .set(Outbox::getFinalizedAt, finalizedAt);
        return mapper.update(updateWrapper);
    }

    public int batchUpdateStatusToFinalize(List<Long> outboxIds, OutboxStatus oldStatus, OutboxStatus newStatus, LocalDateTime finalizedAt) {
        var updateWrapper = Wrappers.<Outbox>lambdaUpdate()
                .in(Outbox::getId, outboxIds)
                .eq(Outbox::getOutboxStatus, oldStatus)
                .set(Outbox::getOutboxStatus, newStatus)
                .set(Outbox::getFinalizedAt, finalizedAt);
        return mapper.update(updateWrapper);
    }

    public int batchClaim(List<Long> outboxIds, LocalDateTime nextAttemptAt) {
        var updateWrapper = Wrappers.<Outbox>lambdaUpdate()
                .in(Outbox::getId, outboxIds)
                .set(Outbox::getNextAttemptAt, nextAttemptAt)
                .setSql("attempt_count=attempt_count+1");
        return mapper.update(updateWrapper);
    }

    public List<Outbox> selectForClaimSkipLock(LocalDateTime attemptAt, int maxRetries, long lastId, int limit) {
        var query = Wrappers.<Outbox>lambdaQuery()
                .eq(Outbox::getOutboxStatus, OutboxStatus.PENDING)
                .lt(Outbox::getAttemptCount, maxRetries)
                .le(Outbox::getNextAttemptAt, attemptAt)
                .ge(Outbox::getId, lastId)
                .orderByAsc(Outbox::getId)
                .last("limit " + limit + " for update skip locked");
        return mapper.selectList(query);
    }

    private boolean sameIntent(Outbox intended, Outbox existing) {
        return Objects.equals(intended.getEventType(), existing.getEventType())
                && Objects.equals(intended.getEventId(), existing.getEventId())
                && Objects.equals(intended.getCommandId(), existing.getCommandId())
                && Objects.equals(intended.getDestination(), existing.getDestination())
                && Objects.equals(intended.getPartitionKey(), existing.getPartitionKey())
                && Arrays.equals(intended.getPayload(), existing.getPayload());
    }
}
