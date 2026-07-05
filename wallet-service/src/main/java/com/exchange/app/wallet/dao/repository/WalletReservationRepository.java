package com.exchange.app.wallet.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.app.wallet.dao.mapper.WalletReservationMapper;
import com.exchange.app.wallet.exception.WalletExceptionFactory;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.common.db.manager.DbBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class WalletReservationRepository extends DbBaseRepository<WalletReservation, WalletReservationMapper> {
    @Autowired
    public WalletReservationRepository(WalletReservationMapper mapper) {
        super(mapper);
    }

    public List<WalletReservation> selectByTxnId(String txnId) {
        return selectByTxnId(txnId, null);
    }

    public List<WalletReservation> selectByTxnId(String txnId, LambdaQueryWrapper<WalletReservation> queryWrapper) {
        queryWrapper = Objects.requireNonNullElseGet(queryWrapper, LambdaQueryWrapper::new);
        queryWrapper = queryWrapper.eq(WalletReservation::getReserveTxnId, txnId);
        return mapper.selectList(queryWrapper);
    }

    public List<WalletReservation> selectByRefs(List<String> refs) {
        if (refs.isEmpty()) {
            return new ArrayList<>();
        }

        var wrapper = queryLambdaWrapper().in(WalletReservation::getReferenceId, refs);
        return mapper.selectList(wrapper);
    }

    public List<WalletReservation> selectInIdForUpdate(List<Long> ids) {
        LambdaUpdateWrapper<WalletReservation> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper = updateWrapper.in(WalletReservation::getId, ids).last("for update");
        return mapper.selectList(updateWrapper);
    }

    // id is explicit and necessary to force using PK in db txn
    // pending-settle -> consumed
    // TODO restrict update fields, only update status and amount
//    public int fullySettleReservation(Long id, WalletReservation reservation, Long amount) {
//        LambdaUpdateWrapper<WalletReservation> updateWrapper = new LambdaUpdateWrapper<>();
//        updateWrapper = updateWrapper.eq(WalletReservation::getReserveTxnId, reservation.getReserveTxnId())
//                .eq(WalletReservation::getId, id)
//                .eq(WalletReservation::getWalletId, reservation.getWalletId())
//                .eq(WalletReservation::getAssetId, reservation.getAssetId())
//                .eq(WalletReservation::getPendingSettle, amount)
//                .eq(WalletReservation::getReserveTxnId, reservation.getReserveTxnId())
//                .eq(WalletReservation::getReservationStatus, ReservationStatus.ACTIVE)
//                .set(WalletReservation::getPendingSettle, 0)
//                .set(WalletReservation::getConsumed, amount)
//                .set(WalletReservation::getReservationStatus, ReservationStatus.FINISHED)
//                .set(WalletReservation::getReservationOutcome, ReservationOutcome.CONSUMED);
//        return mapper.update(updateWrapper);
//    }

    // update with optimistic lock of amount and status
    // update with all fields of now
    public int updateWithInOptimisticLock(WalletReservation now, WalletReservation old) {
        var wrapper = updateLambdaWrapper().eq(WalletReservation::getId, now.getId())
                .eq(WalletReservation::getReservationStatus, old.getReservationStatus())
                .eq(WalletReservation::getRemaining, old.getRemaining())
                .eq(WalletReservation::getPendingSettle, old.getPendingSettle())
                .eq(WalletReservation::getConsumed, old.getConsumed())
                .eq(WalletReservation::getReleased, old.getReleased());
        return mapper.update(now, wrapper);
    }

    public int batchInsert(List<WalletReservation> list) {
        if (list.isEmpty()) {
            throw WalletExceptionFactory.invalidValue("wallet reservation batch insert list must not be empty");
        }
        return mapper.batchInsert(list);
    }
}
