package com.exchange.app.wallet.config;

import com.exchange.common.metrics.GrpcServerMetricsInterceptor;
import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletMetricsConfig {
    // Register shared transport metrics once for every wallet gRPC endpoint.
    @GrpcGlobalServerInterceptor
    public ServerInterceptor grpcServerMetricsInterceptor(MeterRegistry meterRegistry) {
        return new GrpcServerMetricsInterceptor(meterRegistry);
    }
}
