package com.exchange.app.ledger.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum Direction {
    UNKNOWN(0),
    DEBIT(1),
    CREDIT(2),;

    @EnumValue
    final public int code;

    Direction(int code) {
        this.code = code;
    }
}
