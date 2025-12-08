package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TransactionType {
    UNKNOWN(0),
    DIRECT(1),
    RESERVE(2),
    CONSUME(3),
    RELEASE(4),
    ADJUST(5),;

    @EnumValue
    final public int code;

    TransactionType(int code) {
        this.code = code;
    }
}
