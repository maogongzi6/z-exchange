package com.exchange.app.wallet.po.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.outbox.OutboxEventType;
import com.exchange.app.wallet.po.enums.outbox.OutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wallet_outbox")
public class WalletOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private OutboxEventType eventType;
    private String referenceId;
    private String idempotencyKey;
    private OutboxStatus outboxStatus;
    private String payload;
    private Integer attemptCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime finalizedAt;

    private WalletOutbox(OutboxEventType eventType, String referenceId, String idempotencyKey, OutboxStatus outboxStatus, String payload, Integer attemptCount, LocalDateTime nextAttemptAt) {
        this.eventType = eventType;
        this.referenceId = referenceId;
        this.idempotencyKey = idempotencyKey;
        this.outboxStatus = outboxStatus;
        this.payload = payload;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
    }

    public static WalletOutbox create(OutboxEventType eventType, String referenceId, String idempotencyKey, OutboxStatus outboxStatus, String payload, Integer attemptCount, LocalDateTime nextAttemptAt) {
        return new WalletOutbox(eventType, referenceId, idempotencyKey, outboxStatus, payload, attemptCount, nextAttemptAt);
    }
}
