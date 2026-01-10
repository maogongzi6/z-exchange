package com.exchange.app.wallet.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.app.wallet.po.transaction.WalletAction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WalletActionMapper extends BaseMapper<WalletAction> {
    @Insert("<script>" +
            "INSERT INTO wallet_actions (action_id, txn_id, wallet_id, asset_id, bucket, action_type, amount, reservation_id) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.actionId}, #{item.txnId}, #{item.walletId}, #{item.assetId}, #{item.bucket}, #{item.actionType}, #{item.amount}, #{item.reservationId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<WalletAction> list);
}
