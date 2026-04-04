package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.proto.wallet.wallet.BalanceSnapshotPb;

public class PbConverter {
    public static BalanceSnapshotPb convertToBalanceSnapshotPb(BalanceSnapshot snapshot) {
        if (snapshot == null) {
            return BalanceSnapshotPb.newBuilder().build();
        }
        return BalanceSnapshotPb.newBuilder()
                .setWalletId(snapshot.getWalletId())
                .setServiceId(EnumPbMappers.serviceIdPbMapper.from(snapshot.getServiceId()))
                .setWalletReferenceId(snapshot.getWalletReferenceId())
                .setAssetId(snapshot.getAssetId())
                .setWalletStatus(EnumPbMappers.walletStatusPbMapper.from(snapshot.getWalletStatus()))
                .setOwnerType(EnumPbMappers.ownerTypePbMapper.from(snapshot.getOwnerType()))
                .setOwnerId(snapshot.getOwnerId())
                .setAvailable(snapshot.getAvailable())
                .setReserved(snapshot.getReserved())
                .build();
    }
}
