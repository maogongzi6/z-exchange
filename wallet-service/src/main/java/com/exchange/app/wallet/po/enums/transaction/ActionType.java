package com.exchange.app.wallet.po.enums.transaction;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ActionType {
    UNKNOWN(0),
    RESERVE(1),
    CONSUME(2),
    RELEASE(3),
    TRANSFER_OUT(4),
    TRANSFER_IN(5),
    ADJUST(6),;

    @EnumValue
    final public int code;

    ActionType(int code) {
        this.code = code;
    }

    public boolean isTransfer() {
        return this == TRANSFER_OUT || this == TRANSFER_IN;
    }
}
