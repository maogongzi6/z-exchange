package com.exchange.common.utils.time;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class LongTimeHelper {
    static public long now() {
        return LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
