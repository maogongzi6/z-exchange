package com.exchange.common.utils;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

public class StableHashHelper {
    static public String stableHash(final String str) {
        return String.valueOf(Hashing.murmur3_128().hashString(str, StandardCharsets.UTF_8).asLong());
    }
}
