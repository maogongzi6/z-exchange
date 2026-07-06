package com.exchange.common.utils;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class ThrowableUtils {
    private ThrowableUtils() {
    }

    public static Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && current.getCause() != null && seen.add(current)) {
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return current;
    }

    public static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        if (type == null) {
            return null;
        }

        Throwable current = throwable;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && seen.add(current)) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
