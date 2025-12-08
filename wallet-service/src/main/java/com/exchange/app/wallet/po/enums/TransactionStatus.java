package com.exchange.app.wallet.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TransactionStatus {
    UNKNOWN(0),
    PENDING(1),
    COMPLETE(2),
    CLOSED(3),;

    @EnumValue
    final public int code;

    TransactionStatus(int code) {
        this.code = code;
    }
}
