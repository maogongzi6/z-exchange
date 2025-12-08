package com.exchange.app.wallet.po.transaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.ReservationOutcome;
import com.exchange.app.wallet.po.enums.ReservationStatus;
import com.exchange.app.wallet.po.enums.ServiceId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wallet_reservations")
public class WalletReservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reservationId;
    private ServiceId initiator;
    private String referenceId;
    private String walletId;
    private String assetId;
    private Long reservedAmount;
    private Long consumedAmount;
    private ReservationStatus reservationStatus;
    private ReservationOutcome reservationOutcome;
    private String reserveTxnId;
}
