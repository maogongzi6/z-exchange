package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AccountCategory {
    UNKNOWN(0),
    ASSET(1),
    LIABILITY(2),;

    @EnumValue
    final public int code;

    AccountCategory(int code) {
        this.code = code;
    }
}
