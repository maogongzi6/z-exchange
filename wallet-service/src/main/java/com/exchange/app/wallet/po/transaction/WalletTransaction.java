package com.exchange.app.wallet.po.transaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.TransactionStatus;
import com.exchange.app.wallet.po.enums.TransactionType;
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
    private String idempotentKey;
    private TransactionStatus txnStatus;
    private TransactionType txnType;
    private BusinessType businessType;

    private WalletTransaction(BusinessType businessType, TransactionType txnType, String txnId, String referenceId, ServiceId initiator, String idempotentKey, TransactionStatus txnStatus) {
        this.businessType = businessType;
        this.txnType = txnType;
        this.txnId = txnId;
        this.referenceId = referenceId;
        this.initiator = initiator;
        this.idempotentKey = idempotentKey;
        this.txnStatus = txnStatus;
    }

    public static WalletTransaction create(BusinessType businessType, TransactionType txnType, String txnId, String referenceId, ServiceId initiator, String idempotentKey, TransactionStatus txnStatus) {
        return new WalletTransaction(businessType, txnType, txnId, referenceId, initiator, idempotentKey, txnStatus);
    }
}
