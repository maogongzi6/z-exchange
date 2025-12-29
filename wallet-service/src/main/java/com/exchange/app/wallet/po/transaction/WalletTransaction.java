package com.exchange.app.wallet.po.transaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.result.Result;
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
    private ServiceId initiator;
    private String idempotencyKey;
    private TransactionStatus txnStatus;
    private TransactionType txnType;
    private BusinessType businessType;

    private WalletTransaction(String txnId, String referenceId, ServiceId initiator, String idempotencyKey, TransactionStatus txnStatus, TransactionType txnType, BusinessType businessType) {
        this.txnId = txnId;
        this.referenceId = referenceId;
        this.initiator = initiator;
        this.idempotencyKey = idempotencyKey;
        this.txnStatus = txnStatus;
        this.txnType = txnType;
        this.businessType = businessType;
    }

    public static WalletTransaction create(String txnId, String referenceId, ServiceId initiator, String idempotentKey, TransactionStatus txnStatus, TransactionType txnType, BusinessType businessType) {
        return new WalletTransaction(txnId, referenceId, initiator, idempotentKey, txnStatus, txnType, businessType);
    }
}
