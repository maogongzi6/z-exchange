package com.exchange.app.wallet.po.enums.transaction;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ActionType {
    UNKNOWN(0),
    RESERVE(1),
    TRANSFER_OUT(2),
    TRANSFER_IN(3),
    RELEASE(4),
    ADJUST(5),;

    @EnumValue
    final public int code;

    ActionType(int code) {
        this.code = code;
    }
}
