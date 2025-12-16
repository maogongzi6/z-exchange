package com.exchange.app.wallet.dao.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.app.wallet.dao.mapper.BalanceSnapshotMapper;
import com.exchange.app.wallet.exception.InvalidValueException;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.db.DbBaseManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class BalanceSnapshotManager extends DbBaseManager<BalanceSnapshot, BalanceSnapshotMapper> {
    @Autowired
    public BalanceSnapshotManager(BalanceSnapshotMapper mapper) {
        super(mapper);
    }

    public List<BalanceSnapshot> selectIdByRefs(ServiceId serviceId, List<String> walletRefs) {
        LambdaQueryWrapper<BalanceSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper = wrapper.select(BalanceSnapshot::getId, BalanceSnapshot::getWalletId, BalanceSnapshot::getServiceId, BalanceSnapshot::getWalletReferenceId)
                .eq(BalanceSnapshot::getServiceId, serviceId)
                .in(BalanceSnapshot::getWalletReferenceId, walletRefs);
        return this.mapper.selectList(wrapper);
    }

    public int reserveFromWalletId(Long id, String walletId, String assetId, long absAmount) {
        if (absAmount <= 0) {
            throw new InvalidValueException("amount<=0, amount: " + absAmount);
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getId, id)
                .eq(BalanceSnapshot::getWalletId, walletId)
                .eq(BalanceSnapshot::getAssetId, assetId)
                .eq(BalanceSnapshot::getWalletStatus, WalletStatus.OPEN)
                .ge(BalanceSnapshot::getAvailable, absAmount)
                .setSql(String.format("`available` = `available` - %d", absAmount))
                .setSql(String.format("`reserved` = `reserved` + %d", absAmount));
        return this.mapper.update(wrapper);
    }

    public int transferOutFromWalletId(Long id, String walletId, String assetId, long absAmount) {
        if (absAmount <= 0) {
            throw new InvalidValueException("amount<=0, amount: " + absAmount);
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getId, id)
                .eq(BalanceSnapshot::getWalletId, walletId)
                .eq(BalanceSnapshot::getAssetId, assetId)
                .eq(BalanceSnapshot::getWalletStatus, WalletStatus.OPEN)
                .ge(BalanceSnapshot::getReserved, absAmount)
                .setSql(String.format("`reserved` = `reserved` - %d", absAmount));
        return this.mapper.update(wrapper);
    }

    public int transferInToWalletId(Long id, String walletId, String assetId, long absAmount) {
        if (absAmount <= 0) {
            throw new InvalidValueException("amount<=0, amount: " + absAmount);
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getId, id)
                .eq(BalanceSnapshot::getWalletId, walletId)
                .eq(BalanceSnapshot::getAssetId, assetId)
                .eq(BalanceSnapshot::getWalletStatus, WalletStatus.OPEN)
                .setSql(String.format("`available` = `available` + %d", absAmount));
        return this.mapper.update(wrapper);
    }
}
