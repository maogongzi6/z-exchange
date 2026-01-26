package com.exchange.app.wallet.po.enums.transaction;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TransactionStatus {
    UNKNOWN(0),
    PENDING(1),
    COMPLETED(2),
    CLOSED(3),;

    @EnumValue
    final public int code;

    TransactionStatus(int code) {
        this.code = code;
    }

    public boolean hasFinalized() {
        return this == COMPLETED || this == CLOSED;
    }
}
