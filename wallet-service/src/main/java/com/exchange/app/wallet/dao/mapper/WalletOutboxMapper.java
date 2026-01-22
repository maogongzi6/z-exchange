//package com.exchange.app.wallet.dao.mapper;
//
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import com.exchange.app.wallet.po.outbox.WalletOutbox;
//import com.exchange.app.wallet.po.transaction.WalletTransaction;
//import org.apache.ibatis.annotations.Param;
//import org.apache.ibatis.annotations.Select;
//
//public interface WalletOutboxMapper extends BaseMapper<WalletOutbox> {
//    @Select("SELECT * FROM wallet_outbox where idempotency_key=#{idempotency_key}")
//    public WalletOutbox selectByIdempotencyKey(@Param("idempotency_key")String key);
//}
