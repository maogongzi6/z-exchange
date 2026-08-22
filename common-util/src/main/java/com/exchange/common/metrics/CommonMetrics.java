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

    public static final MetricDef DB_TRANSACTION_DURATION = new MetricDef(
            "zexchange.db.transaction.duration",
            "Client-observed DB transaction duration"
    );

    public static final MetricDef DB_OPERATION_DURATION = new MetricDef(
            "zexchange.db.operation.duration",
            "Client-observed MyBatis statement execution duration"
    );

    public static final MetricDef DB_OPERATION_ERRORS = new MetricDef(
            "zexchange.db.operation.errors",
            "MyBatis statement execution error count"
    );

    public static final MetricDef KAFKA_PUBLISHER_REQUESTS = new MetricDef(
            "zexchange.kafka.publisher.requests",
            "Kafka publisher attempt count completed by broker outcome"
    );

    public static final MetricDef KAFKA_PUBLISHER_DURATION = new MetricDef(
            "zexchange.kafka.publisher.duration",
            "Client-observed Kafka publish duration through broker acknowledgment"
    );

    public static final MetricDef OUTBOX_DELIVERY_DELAY = new MetricDef(
            "zexchange.outbox.delivery.delay",
            "Elapsed time from outbox creation to completion of a Kafka publish attempt"
    );

    public static final MetricDef KAFKA_LISTENER_REQUESTS = new MetricDef(
            "zexchange.kafka.listener.requests",
            "Kafka listener invocation count by final handling outcome"
    );

    public static final MetricDef KAFKA_LISTENER_DURATION = new MetricDef(
            "zexchange.kafka.listener.duration",
            "Kafka listener invocation duration"
    );

    public static final MetricDef KAFKA_LISTENER_PROCESSING_DELAY = new MetricDef(
            "zexchange.kafka.listener.processing.delay",
            "Elapsed time from the original Kafka record timestamp to listener processing"
    );

    public static final MetricDef KAFKA_LISTENER_DLT = new MetricDef(
            "zexchange.kafka.listener.dlt",
            "Kafka records consumed by a listener's dead-letter handler"
    );
}
