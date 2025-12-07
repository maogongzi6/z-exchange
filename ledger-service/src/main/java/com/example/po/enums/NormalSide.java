package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum NormalSide {
    UNKNOWN(0),
    DEBIT(1),
    CREDIT(2);

    @EnumValue
    final public int code;

    NormalSide(int code) {
        this.code = code;
    }
}
