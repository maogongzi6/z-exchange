package com.exchange.app.wallet.dao.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exchange.app.wallet.dao.mapper.BalanceSnapshotMapper;
import com.exchange.app.wallet.exception.InvalidValueException;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.db.manager.DbBaseManager;
import lombok.extern.slf4j.Slf4j;
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

    public List<BalanceSnapshot> selectByWalletIds(List<String> walletIds) {
        var query = Wrappers.<BalanceSnapshot>lambdaQuery().in(BalanceSnapshot::getWalletId, walletIds);
        return mapper.selectList(query);
    }

    public List<BalanceSnapshot> selectIdByRefs(ServiceId serviceId, List<String> walletRefs) {
        LambdaQueryWrapper<BalanceSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper = wrapper.select(BalanceSnapshot::getId, BalanceSnapshot::getWalletId, BalanceSnapshot::getServiceId, BalanceSnapshot::getWalletReferenceId)
                .eq(BalanceSnapshot::getServiceId, serviceId)
                .in(BalanceSnapshot::getWalletReferenceId, walletRefs);
        return this.mapper.selectList(wrapper);
    }

    public List<BalanceSnapshot> selectByRefs(ServiceId serviceId, List<String> walletRefs) {
        LambdaQueryWrapper<BalanceSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper = wrapper.eq(BalanceSnapshot::getServiceId, serviceId)
                .in(BalanceSnapshot::getWalletReferenceId, walletRefs);
        return this.mapper.selectList(wrapper);
    }

    public int reserveFromWalletId(Long id, String walletId, String assetId, long amount) {
        if (amount <= 0) {
            throw new InvalidValueException("amount<=0, amount: " + amount);
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getId, id)
                .eq(BalanceSnapshot::getWalletId, walletId)
                .eq(BalanceSnapshot::getAssetId, assetId)
                .eq(BalanceSnapshot::getWalletStatus, WalletStatus.OPEN)
                .ge(BalanceSnapshot::getAvailable, amount)
                .setSql(String.format("`available` = `available` - %d", amount))
                .setSql(String.format("`reserved` = `reserved` + %d", amount));
        return this.mapper.update(wrapper);
    }

    public int releaseFromWalletId(Long id, String walletId, String assetId, long amount) {
        if (amount <= 0) {
            throw new InvalidValueException("amount<=0, amount: " + amount);
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getId, id)
                .eq(BalanceSnapshot::getWalletId, walletId)
                .eq(BalanceSnapshot::getAssetId, assetId)
                .eq(BalanceSnapshot::getWalletStatus, WalletStatus.OPEN)
                .ge(BalanceSnapshot::getReserved, amount)
                .setSql(String.format("`available` = `available` + %d", amount))
                .setSql(String.format("`reserved` = `reserved` - %d", amount));
        return this.mapper.update(wrapper);
    }

    public int transferOutFromWalletId(Long id, String walletId, String assetId, long amount) {
        if (amount <= 0) {
            throw new InvalidValueException("amount<=0, amount: " + amount);
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getId, id)
                .eq(BalanceSnapshot::getWalletId, walletId)
                .eq(BalanceSnapshot::getAssetId, assetId)
                .eq(BalanceSnapshot::getWalletStatus, WalletStatus.OPEN)
                .ge(BalanceSnapshot::getReserved, amount)
                .setSql(String.format("`reserved` = `reserved` - %d", amount));
        return this.mapper.update(wrapper);
    }

    public int transferInToWalletId(Long id, String walletId, String assetId, long amount) {
        if (amount <= 0) {
            throw new InvalidValueException("amount<=0, amount: " + amount);
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getId, id)
                .eq(BalanceSnapshot::getWalletId, walletId)
                .eq(BalanceSnapshot::getAssetId, assetId)
                .eq(BalanceSnapshot::getWalletStatus, WalletStatus.OPEN)
                .setSql(String.format("`available` = `available` + %d", amount));
        return this.mapper.update(wrapper);
    }
}
