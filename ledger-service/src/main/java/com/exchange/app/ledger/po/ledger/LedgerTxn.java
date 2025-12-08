package com.exchange.app.ledger.po.ledger;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ledger_transactions")
public class LedgerTxn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String txnId;
    private String referenceId;
    private String extraData;

    public static LedgerTxn create(String txnId, String referenceId, String extraData) {
        return new LedgerTxn(0L, txnId, referenceId, extraData);
    }
}
