package com.example.po.wallet;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.po.enums.OwnerType;
import com.example.po.enums.ServiceId;
import com.example.po.enums.WalletStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wallets")
public class Wallet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String walletId;
    private ServiceId serviceId;
    private String referenceId;
    private String assetId;
    private WalletStatus walletStatus;
    private OwnerType ownerType;
    private String ownerId;

    public static Wallet create(String walletId, ServiceId serviceId, String referenceId, String assetId, WalletStatus walletStatus, OwnerType ownerType, String ownerId) {
        return new Wallet(0L, walletId, serviceId, referenceId, assetId, walletStatus, ownerType, ownerId);
    }
}