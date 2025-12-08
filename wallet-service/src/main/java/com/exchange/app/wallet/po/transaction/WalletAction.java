package com.exchange.app.wallet.po.transaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.ActionType;
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
    private ActionType actionType;
    private Long amount;
    private String reservationId;

    private WalletAction(String actionId, String txnId, String walletId, String assetId, ActionType actionType, Long amount, String reservationId) {
        this.actionId = actionId;
        this.txnId = txnId;
        this.walletId = walletId;
        this.assetId = assetId;
        this.actionType = actionType;
        this.amount = amount;
        this.reservationId = reservationId;
    }

    public static WalletAction create(String actionId, String txnId, String walletId, String assetId, ActionType actionType, Long amount, String reservationId) {
        return new WalletAction(actionId, txnId, walletId, assetId, actionType, amount, reservationId);
    }
}
