package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ServiceId {
    UNKNOWN(0),
    SYSTEM(1),
    WALLET(2);

    @EnumValue
    final public int code;

    ServiceId(int code) {
        this.code = code;
    }
}
