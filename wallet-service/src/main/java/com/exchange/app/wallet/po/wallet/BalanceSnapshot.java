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
@TableName("balance_snapshots")
public class BalanceSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotId;
    private String walletId;
    private String assetId;
    private Long available;
    private Long hold;

    public static BalanceSnapshot create(String snapshotId, String walletId, String assetId, Long available, Long hold) {
        return new BalanceSnapshot(0L, snapshotId, walletId, assetId, available, hold);
    }
}
