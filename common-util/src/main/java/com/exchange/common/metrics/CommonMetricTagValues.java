package com.exchange.common.metrics;

public final class CommonMetricTagValues {
    public static final String UNKNOWN = "unknown";
    public static final String CARDINALITY_OVERFLOW = "cardinality_overflow";

    public static final class Outcomes {
        public static final String SUCCESS = "success";
        public static final String ERROR = "error";

        private Outcomes() {
        }
    }

    public static final class CacheOperations {
        public static final String GET = "get";
        public static final String AFTER_DB_HIT = "after_db_hit";
        public static final String AFTER_DB_MISS = "after_db_miss";
        public static final String AFTER_UPDATE = "after_update";
        public static final String AFTER_INSERT = "after_insert";

        private CacheOperations() {
        }
    }

    public static final class CacheResults {
        public static final String VALUE_HIT = "value_hit";
        public static final String MISS = "miss";
        public static final String TOMBSTONE_HIT = "tombstone_hit";
        public static final String NEGATIVE_HIT = "negative_hit";
        public static final String ERROR = "error";

        private CacheResults() {
        }
    }

    public static final class CacheErrorTypes {
        public static final String REDIS_ERROR = "redis_error";
        public static final String TIMEOUT = "timeout";
        public static final String ACCESS = "access";
        public static final String SCRIPT_ERROR = "script_error";
        public static final String CONFIGURATION_ERROR = "configuration_error";
        public static final String DECODE_ERROR = "decode_error";
        public static final String CONTRACT_VIOLATION = "contract_violation";
        public static final String UNKNOWN = "unknown";

        private CacheErrorTypes() {
        }
    }

    public static final class RedisComponents {
        public static final String REDIS_TEMPLATE = "redis_template";
        public static final String REDISSON_CLIENT_SIDE = "redisson_client_side";
        public static final String SCRIPT_EXECUTOR = "script_executor";

        private RedisComponents() {
        }
    }

    public static final class RedisOperations {
        public static final String GET = "get";
        public static final String SET = "set";
        public static final String DELETE = "delete";
        public static final String SET_IF_ABSENT = "set_if_absent";
        public static final String SET_IF_ABSENT_OR_NEWER = "set_if_absent_or_newer";
        public static final String RELEASE_IDEMP_IF_OWNED = "release_idemp_if_owned";

        private RedisOperations() {
        }
    }

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

    public static final class DbCommands {
        public static final String SELECT = "select";
        public static final String INSERT = "insert";
        public static final String UPDATE = "update";
        public static final String DELETE = "delete";
        public static final String UNKNOWN = "unknown";

        private DbCommands() {
        }
    }

    private CommonMetricTagValues() {
    }
}
