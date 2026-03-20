package com.exchange.common.utils;

import org.springframework.data.util.Pair;

public class StringHelper {
    static public String removePrefix(String str, String prefix) {
        if (str.startsWith(prefix)) {
            StringBuilder sb = new StringBuilder(str);
            sb.delete(0, prefix.length());
            return sb.toString();
        }
        return str;
    }

    // strictly divide the value into two parts,
    // return {"", ""} if it cannot be divided
    static public Pair<String, String> strictDivideIntoTwoParts(String value, String divider) {

        if (ValidateHelper.isEmpty(divider) || ValidateHelper.isEmpty(value)) {
            return Pair.of("", "");
        }
        int i = value.indexOf(divider);
        if (i < 0) {
            return Pair.of("", "");
        }
        String first = value.substring(0, i);
        String second = value.substring(i + 1);
        return Pair.of(first, second);
    }
}
