package com.exchange.app.ledger.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AccountStatus {
    UNKNOWN(0),
    OPEN(1),
    CLOSE(2);

    @EnumValue
    final public int code;

    AccountStatus(int code) {
        this.code = code;
    }
}
