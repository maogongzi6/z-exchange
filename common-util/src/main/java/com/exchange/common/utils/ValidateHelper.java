package com.exchange.common.utils;

public class ValidateHelper {
    public static boolean isEmpty(Object value) {
        if (value instanceof String) {
            return isBlank((String) value);
        }
        return value == null;
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean hasText(String value) {
        return !isBlank(value);
    }
}
