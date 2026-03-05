package com.exchange.app.ledger.po.ledger;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.exchange.common.db.po.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ledger_transactions")
public class LedgerTxn extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String txnId;
    private String referenceId;
    private String metadata;
    @Version
    private Long version;

    public static LedgerTxn create(String txnId, String referenceId, String metadata) {
        return new LedgerTxn(0L, txnId, referenceId, metadata, 0L);
    }

    public static LedgerTxn create(String txnId, String referenceId) {
        return new LedgerTxn(0L, txnId, referenceId, "{}", 0L);
    }
}
