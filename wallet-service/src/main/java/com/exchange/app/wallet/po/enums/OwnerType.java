package com.exchange.app.wallet.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum OwnerType {
    UNKNOWN(0),
    SYSTEM(1),
    USER(2),;

    @EnumValue
    final public int code;

    OwnerType(int code) {
        this.code = code;
    }
}
