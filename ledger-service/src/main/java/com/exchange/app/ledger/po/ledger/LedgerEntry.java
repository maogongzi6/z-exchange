package com.exchange.app.ledger.po.ledger;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.ledger.po.enums.Direction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ledger_entries")
public class LedgerEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entryId;
    private String txnId;
    private String assetId;
    private String accountId;
    private long amount;
    private Direction direction;

    public static LedgerEntry create(String entryId, String txnId, String assetId, String accountId, long amount, Direction direction) {
        return new LedgerEntry(0L, entryId, txnId, assetId, accountId, amount, direction);
    }
}
