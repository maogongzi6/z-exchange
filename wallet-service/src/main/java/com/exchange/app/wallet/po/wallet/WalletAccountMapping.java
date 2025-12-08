package com.exchange.app.wallet.po.wallet;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wallet_account_mappings")
public class WalletAccountMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String walletId;
    // now the ref id is wallet_id, because wallet to account is 1:1 mapping
    private String accountRefId;

    public static WalletAccountMapping create(String walletId, String accountRefId) {
        return new WalletAccountMapping(0L, walletId, accountRefId);
    }
}
