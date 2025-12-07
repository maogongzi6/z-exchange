package com.example.po.account;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.po.enums.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("accounts")
public class Account {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String accountId;
    private ServiceId serviceId;
    private String referenceId;
    private AccountCategory category;
    private NormalSide normalSide;
    private String ownerId;
    private OwnerType ownerType;
    private String assetId;
    private AccountStatus accountStatus;

    public static Account create(String accountId, ServiceId serviceId, String referenceId, AccountCategory category, NormalSide normalSide, String ownerId, OwnerType ownerType, String assetId, AccountStatus accountStatus) {
        return new Account(0L, accountId, serviceId, referenceId, category, normalSide, ownerId, ownerType, assetId, accountStatus);
    }
}