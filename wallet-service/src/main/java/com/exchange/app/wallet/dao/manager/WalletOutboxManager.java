package com.exchange.app.wallet.dao.manager;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.app.wallet.dao.mapper.WalletOutboxMapper;
import com.exchange.app.wallet.dao.mapper.WalletTransactionMapper;
import com.exchange.app.wallet.po.enums.outbox.OutboxStatus;
import com.exchange.app.wallet.po.outbox.WalletOutbox;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.common.db.DbBaseManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class WalletOutboxManager extends DbBaseManager<WalletOutbox, WalletOutboxMapper> {
    @Autowired
    public WalletOutboxManager(WalletOutboxMapper walletOutboxMapper) {
        super(walletOutboxMapper);
    }

    public WalletOutbox selectByIdempotencyKey(String idempotencyKey) {
        return mapper.selectByIdempotencyKey(idempotencyKey);
    }

    public int finishOutbox(WalletOutbox walletOutbox) {
        LambdaUpdateWrapper<WalletOutbox> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(WalletOutbox::getId, walletOutbox.getId())
                .eq(WalletOutbox::getEventType , walletOutbox.getEventType())
                .eq(WalletOutbox::getOutboxStatus, OutboxStatus.PENDING)
                .set(WalletOutbox::getOutboxStatus, OutboxStatus.SENT)
                .set(WalletOutbox::getLastAttemptAt, LocalDateTime.now())
                .setSql("attempt_count = attempt_count + 1");
        return mapper.update(updateWrapper);
    }
}