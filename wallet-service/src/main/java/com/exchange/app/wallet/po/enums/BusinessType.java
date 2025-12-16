package com.exchange.app.wallet.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum BusinessType {
    UNKNOWN(0),
    TRANSFER(1);

    @EnumValue
    final public int code;

    BusinessType(int code) {
        this.code = code;
    }
}
