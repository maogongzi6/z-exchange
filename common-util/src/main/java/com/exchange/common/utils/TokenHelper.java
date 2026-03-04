package com.exchange.common.utils;

import java.util.UUID;

public class TokenHelper {
    static public String generateToken(String serviceName) {
        return serviceName + "-" + UUID.randomUUID();
    }
}
