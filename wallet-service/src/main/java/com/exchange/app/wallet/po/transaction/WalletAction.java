package com.exchange.app.wallet.po.transaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.transaction.ActionType;
import com.exchange.app.wallet.po.enums.WalletBucket;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wallet_actions")
public class WalletAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String actionId;
    private String txnId;
    private String walletId;
    private String assetId;
    private WalletBucket bucket;
    private ActionType actionType;
    private Long amount;
    private String reservationId;

    private WalletAction(String actionId, String txnId, String walletId, String assetId, WalletBucket bucket, ActionType actionType, Long amount, String reservationId) {
        this.actionId = actionId;
        this.txnId = txnId;
        this.walletId = walletId;
        this.assetId = assetId;
        this.bucket = bucket;
        this.actionType = actionType;
        this.amount = amount;
        this.reservationId = reservationId;
    }

    public boolean isTransfer() {
        return actionType.isTransfer();
    }

    public static WalletAction create(String actionId, String txnId, String walletId, String assetId, WalletBucket bucket, ActionType actionType, Long amount, String reservationId) {
        return new WalletAction(actionId, txnId, walletId, assetId, bucket, actionType, amount, reservationId);
    }
}
