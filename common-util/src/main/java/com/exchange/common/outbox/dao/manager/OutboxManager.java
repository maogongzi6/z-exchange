package com.exchange.common.outbox.dao.manager;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.common.db.manager.DbBaseManager;
import com.exchange.common.outbox.dao.mapper.OutboxMapper;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.outbox.po.enums.OutboxStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OutboxManager extends DbBaseManager<Outbox, OutboxMapper> {
    public OutboxManager(OutboxMapper mapper) {
        super(mapper);
    }

    public int insertWithClaim(Outbox outbox, LocalDateTime nextAttemptAt) {
        outbox.setAttemptCount(1);
        outbox.setNextAttemptAt(nextAttemptAt);
        return insertIgnore(outbox);
    }

    public int updateStatusToFinalize(Outbox outbox, OutboxStatus newStatus, LocalDateTime finalizedAt) {
        var updateWrapper = new LambdaUpdateWrapper<Outbox>();
        updateWrapper.eq(Outbox::getOutboxStatus, outbox.getOutboxStatus())
                .set(Outbox::getOutboxStatus, newStatus)
                .set(Outbox::getFinalizedAt, finalizedAt);
        return mapper.update(updateWrapper);
    }
}
