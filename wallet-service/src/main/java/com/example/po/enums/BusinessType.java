package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum BusinessType {
    UNKNOWN(0),
    ADJUST(1),
    TOP_UP(2),;

    @EnumValue
    final public int code;

    BusinessType(int code) {
        this.code = code;
    }
}
