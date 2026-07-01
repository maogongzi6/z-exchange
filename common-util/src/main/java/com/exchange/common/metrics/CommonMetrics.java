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
}
