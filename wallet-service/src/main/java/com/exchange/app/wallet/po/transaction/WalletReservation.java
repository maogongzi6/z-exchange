package com.exchange.app.wallet.po.transaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exchange.app.wallet.po.enums.transaction.ReservationOutcome;
import com.exchange.app.wallet.po.enums.transaction.ReservationStatus;
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
    private Long pendingSettleAmount;
    private ReservationStatus reservationStatus;
    private ReservationOutcome reservationOutcome;
    private String reserveTxnId;

    private WalletReservation(String reservationId, ServiceId initiator, String referenceId, String walletId, String assetId, Long reservedAmount, Long consumedAmount, Long pendingSettleAmount, ReservationStatus reservationStatus, ReservationOutcome reservationOutcome, String reserveTxnId) {
        this.reservationId = reservationId;
        this.initiator = initiator;
        this.referenceId = referenceId;
        this.walletId = walletId;
        this.assetId = assetId;
        this.reservedAmount = reservedAmount;
        this.consumedAmount = consumedAmount;
        this.pendingSettleAmount = pendingSettleAmount;
        this.reservationStatus = reservationStatus;
        this.reservationOutcome = reservationOutcome;
        this.reserveTxnId = reserveTxnId;
    }

    public static WalletReservation create(String reservationId, ServiceId initiator, String referenceId, String walletId, String assetId, Long reservedAmount, Long consumedAmount, Long pendingSettleAmount, ReservationStatus reservationStatus, ReservationOutcome reservationOutcome, String reserveTxnId) {
        return new WalletReservation(reservationId, initiator, referenceId, walletId, assetId, reservedAmount, consumedAmount, pendingSettleAmount, reservationStatus, reservationOutcome, reserveTxnId);
    }
}
