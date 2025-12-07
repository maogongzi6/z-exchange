package com.example.po.wallet;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.po.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wallet_transactions")
public class WalletTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String txnId;
    private String referenceId;
    private TransactionStatus status;

    public static WalletTransaction create(String txnId, String referenceId, TransactionStatus status) {
        return new WalletTransaction(0L, txnId, referenceId, status);
    }
}
