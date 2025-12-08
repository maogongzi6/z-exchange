package com.exchange.app.wallet.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ServiceId {
    UNKNOWN(0),
    SYSTEM(1),
    USER(2);

    @EnumValue
    final public int code;

    ServiceId(int code) {
        this.code = code;
    }
}
