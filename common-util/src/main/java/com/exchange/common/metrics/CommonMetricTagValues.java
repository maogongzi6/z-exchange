package com.exchange.common.metrics;

public final class CommonMetricTagValues {
    public static final String UNKNOWN = "unknown";

    public static final class IdempotencyOperations {
        public static final String CLAIM = "claim";
        public static final String MARK_DONE = "mark_done";
        public static final String GET = "get";
        public static final String HASH_COMPARE = "hash_compare";
        public static final String RELEASE = "release";
        public static final String FORCE_DELETE = "force_delete";

        private IdempotencyOperations() {
        }
    }

    public static final class IdempotencyErrorTypes {
        public static final String HASH_CONFLICT = "hash_conflict";
        public static final String PARSE_ERROR = "parse_error";
        public static final String REDIS_ERROR = "redis_error";
        public static final String TIMEOUT = "timeout";
        public static final String ACCESS = "access";
        public static final String SCRIPT_ERROR = "script_error";
        public static final String CONFIGURATION_ERROR = "configuration_error";
        public static final String CONTRACT_VIOLATION = "contract_violation";
        public static final String UNKNOWN = "unknown";

        private IdempotencyErrorTypes() {
        }
    }

    private CommonMetricTagValues() {
    }
}
