package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TransactionStatus {
    UNKNOWN(0),
    PENDING(1),
    COMPLETE(2),
    ROLLBACK(3),
    CLOSED(4),;

    @EnumValue
    final public int code;

    TransactionStatus(int code) {
        this.code = code;
    }
}
