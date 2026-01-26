package com.exchange.app.wallet.dao.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exchange.app.wallet.dao.mapper.WalletTransactionMapper;
import com.exchange.app.wallet.exception.InvalidValueException;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.common.db.manager.DbBaseManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
public class WalletTransactionManager extends DbBaseManager<WalletTransaction, WalletTransactionMapper> {
    @Autowired
    public WalletTransactionManager(WalletTransactionMapper walletTransactionMapper) {
        super(walletTransactionMapper);
    }

    public WalletTransaction selectById(Long id) {
        return mapper.selectById(id);
    }

    public WalletTransaction selectByTxnId(String txnId) {
        var query = Wrappers.<WalletTransaction>lambdaQuery().eq(WalletTransaction::getTxnId, txnId);
        return mapper.selectOne(query);
    }

    public WalletTransaction selectByIdempotencyKey(ServiceId serviceId, String idempotencyKey) {
        return selectByIdempotencyKey(serviceId, idempotencyKey, null);
    }

    public WalletTransaction selectByIdempotencyKey(ServiceId serviceId, String idempotencyKey, LambdaQueryWrapper<WalletTransaction> queryWrapper) {
        if (serviceId == null || Strings.isEmpty(idempotencyKey)) {
            throw new InvalidValueException(String.format("serviceId or idempotencyKey is null, serviceId:%s, idempotencyKey:%s", serviceId, idempotencyKey));
        }
        LambdaQueryWrapper<WalletTransaction> query = Objects.requireNonNullElseGet(queryWrapper, LambdaQueryWrapper::new);
        query = query.eq(WalletTransaction::getInitiator, serviceId).eq(WalletTransaction::getIdempotencyKey, idempotencyKey);
        return mapper.selectOne(query);
    }

    public int updateTransactionStatus(Long id, TransactionStatus oldStatus, TransactionStatus newStatus) {
        LambdaUpdateWrapper<WalletTransaction> update = new LambdaUpdateWrapper<>();
        update.eq(WalletTransaction::getId, id).eq(WalletTransaction::getTxnStatus, oldStatus).set(WalletTransaction::getTxnStatus, newStatus);
        return mapper.update(update);
    }
}
