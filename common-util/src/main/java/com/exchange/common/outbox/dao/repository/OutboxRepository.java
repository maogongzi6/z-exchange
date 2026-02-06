package com.exchange.common.outbox.dao.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exchange.common.db.manager.DbBaseRepository;
import com.exchange.common.outbox.dao.mapper.OutboxMapper;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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

    public Outbox selectByCommandId(String commandId) {
        var query = Wrappers.<Outbox>lambdaQuery().eq(Outbox::getCommandId, commandId);
        return mapper.selectOne(query);
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
}
