package com.exchange.app.wallet.config;

import com.exchange.common.metrics.GrpcServerMetricsInterceptor;
import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WalletMetricsConfigTest {
    @Test
    void registersSharedInterceptorAsGlobalGrpcInterceptor() throws NoSuchMethodException {
        // The annotation is the Spring gRPC registration boundary; constructing the bean alone is insufficient.
        assertNotNull(WalletMetricsConfig.class
                .getMethod("grpcServerMetricsInterceptor", io.micrometer.core.instrument.MeterRegistry.class)
                .getAnnotation(GrpcGlobalServerInterceptor.class));

        ServerInterceptor interceptor = new WalletMetricsConfig()
                .grpcServerMetricsInterceptor(new SimpleMeterRegistry());
        assertInstanceOf(GrpcServerMetricsInterceptor.class, interceptor);
    }
}
