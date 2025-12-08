package com.exchange.app.wallet.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum WalletStatus {
    UNKNOWN(0),
    INIT(1),
    OPEN(2),
    FROZEN(3),
    CLOSE(4);

    @EnumValue
    final public int code;

    WalletStatus(int code) {
        this.code = code;
    }
}
