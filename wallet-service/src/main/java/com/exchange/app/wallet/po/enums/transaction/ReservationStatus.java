package com.exchange.app.wallet.po.enums.transaction;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ReservationStatus {
    UNKNOWN(0),
    ACTIVE(1),
    FINISHED(2),
    CLOSED(3),;

    @EnumValue
    final public int code;

    ReservationStatus(int code) {
        this.code = code;
    }
}
