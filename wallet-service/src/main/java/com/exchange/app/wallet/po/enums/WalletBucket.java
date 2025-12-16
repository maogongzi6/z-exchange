package com.exchange.app.wallet.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum WalletBucket {
    UNKNOWN(0),
    AVAILABLE(1),
    RESERVED(2);

    @EnumValue
    final public int code;

    WalletBucket(int code) {
        this.code = code;
    }
}
