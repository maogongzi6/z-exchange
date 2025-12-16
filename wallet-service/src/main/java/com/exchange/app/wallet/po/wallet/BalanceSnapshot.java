package com.exchange.app.wallet.po.wallet;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
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
    private String walletId;
    private ServiceId serviceId;
    private String walletReferenceId;
    private String assetId;
    // consistent with wallet.status
    private WalletStatus walletStatus;
    private OwnerType ownerType;
    private String ownerId;
    private Long available;
    private Long reserved;

    private BalanceSnapshot(String walletId, ServiceId serviceId, String walletReferenceId, String assetId, WalletStatus walletStatus, OwnerType ownerType, String ownerId, Long available, Long reserved) {
        this.walletId = walletId;
        this.serviceId = serviceId;
        this.walletReferenceId = walletReferenceId;
        this.assetId = assetId;
        this.walletStatus = walletStatus;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.available = available;
        this.reserved = reserved;
    }

    public static BalanceSnapshot create(String walletId, ServiceId serviceId, String walletReferenceId, String assetId, WalletStatus walletStatus, OwnerType ownerType, String ownerId, Long available, Long reserved) {
        return new BalanceSnapshot(walletId, serviceId, walletReferenceId, assetId, walletStatus, ownerType, ownerId, available, reserved);
    }
}
