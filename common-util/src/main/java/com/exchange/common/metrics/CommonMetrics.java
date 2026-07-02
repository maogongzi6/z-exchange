package com.exchange.common.metrics;

public final class CommonMetrics {
    private CommonMetrics() {}

    public static final MetricDef GRPC_SERVER_ACTIVE = new MetricDef(
            "zexchange.grpc.server.active",
            "Current in-flight gRPC calls"
    );

    public static final MetricDef GRPC_SERVER_DURATION = new MetricDef(
            "zexchange.grpc.server.duration",
            "Server-side gRPC call duration"
    );

    public static final MetricDef IDEMPOTENCY_ERRORS = new MetricDef(
            "zexchange.idempotency.errors",
            "Idempotency infrastructure or semantic error count"
    );

    public static final MetricDef CACHE_OPS = new MetricDef(
            "zexchange.cache.ops",
            "Strategy-level cache read result count"
    );

    public static final MetricDef CACHE_ERRORS = new MetricDef(
            "zexchange.cache.errors",
            "Strategy-level cache failure count"
    );

    public static final MetricDef REDIS_OPERATION_DURATION = new MetricDef(
            "zexchange.redis.operation.duration",
            "Client-observed Redis operation duration"
    );
}
