package com.example.po.wallet;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.po.enums.ActionType;
import com.example.po.enums.WalletStatus;
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
    private String assetId;
    private ActionType actionType;
    private String creditWalletId;
    private String debitWalletId;
    private Long amount;

    static public WalletAction createPosting(String actionId, String txnId, String assetId, String creditWalletId, String debitWalletId, Long amount) {
        return new WalletAction(0L, actionId, txnId, assetId, ActionType.POSTING, creditWalletId, debitWalletId, amount);
    }
}
