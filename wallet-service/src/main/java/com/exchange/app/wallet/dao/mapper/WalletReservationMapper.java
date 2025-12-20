package com.exchange.app.wallet.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WalletReservationMapper extends BaseMapper<WalletReservation> {
    @Insert("<script>" +
            "INSERT INTO wallet_reservations (reservation_id, initiator, reference_id, wallet_id, asset_id, total, remaining, consumed, pending_settle, reservation_status, reservation_outcome, reserve_txn_id) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.reservationId}, #{item.initiator}, #{item.referenceId}, #{item.walletId}, #{item.assetId}, #{item.total}, #{item.remaining}, #{item.consumed}, #{item.pendingSettle}, #{item.reservationStatus}, #{item.reservationOutcome}, #{item.reserveTxnId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<WalletReservation> list);
}
