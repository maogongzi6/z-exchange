package com.exchange.app.wallet.po.enums.transaction;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TransactionType {
    UNKNOWN(0),
    ATOMIC(1),
    RESERVE(2),
    SETTLE(3),
    ADJUST(4),;

    @EnumValue
    final public int code;

    TransactionType(int code) {
        this.code = code;
    }
}
