package com.exchange.common.metrics;

import io.grpc.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class GrpcServerMetricsInterceptor implements ServerInterceptor {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<MethodKey, AtomicInteger> activeCalls = new ConcurrentHashMap<>();

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        MethodKey methodKey = MethodKey.from(serverCall);
        AtomicInteger counter = getActiveCounter(methodKey);
        AtomicBoolean finished = new AtomicBoolean(false);
        Timer.Sample sample = Timer.start(meterRegistry);

        // register as active
        counter.incrementAndGet();

        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(serverCall) {
            @Override
            public void close(Status status, Metadata trailers) {
                try {
                    super.close(status, trailers);
                } finally {
                    finishOnce(methodKey, sample, counter, finished, status);
                }
            }
        };

        try {
            ServerCall.Listener<ReqT> listener = serverCallHandler.startCall(wrappedCall, metadata);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
                @Override
                public void onMessage(ReqT message) {
                    try {
                        super.onMessage(message);
                    } catch (Throwable throwable) {
                        finishOnce(methodKey, sample, counter, finished, statusFromThrowable(throwable));
                        throw throwable;
                    }
                }

                @Override
                public void onHalfClose() {
                    try {
                        super.onHalfClose();
                    } catch (Throwable throwable) {
                        finishOnce(methodKey, sample, counter, finished, statusFromThrowable(throwable));
                        throw throwable;
                    }
                }

                @Override
                public void onCancel() {
                    try {
                        super.onCancel();
                    } finally {
                        finishOnce(methodKey, sample, counter, finished, Status.CANCELLED);
                    }
                }

                @Override
                public void onComplete() {
                    try {
                        super.onComplete();
                    } catch (Throwable throwable) {
                        finishOnce(methodKey, sample, counter, finished, statusFromThrowable(throwable));
                        throw throwable;
                    }
                }

                @Override
                public void onReady() {
                    try {
                        super.onReady();
                    } catch (Throwable throwable) {
                        finishOnce(methodKey, sample, counter, finished, statusFromThrowable(throwable));
                        throw throwable;
                    }
                }
            };
        } catch (Throwable throwable) {
            finishOnce(methodKey, sample, counter, finished, statusFromThrowable(throwable));
            throw throwable;
        }
    }

    private AtomicInteger getActiveCounter(MethodKey key) {
        return activeCalls.computeIfAbsent(key, k -> {
            var counter = new AtomicInteger(0);
            Gauge.builder(CommonMetrics.GRPC_SERVER_ACTIVE.name(), counter, AtomicInteger::get)
                    .tag("service", key.service)
                    .tag("method", key.method)
                    .description(CommonMetrics.GRPC_SERVER_ACTIVE.description())
                    .register(meterRegistry);

            return counter;
        });
    }

    private void finishOnce(MethodKey methodKey, Timer.Sample sample, AtomicInteger counter, AtomicBoolean finished, Status status) {
        if (!finished.compareAndSet(false, true)) {
            // already finished
            return;
        }

        // unregister active
        counter.decrementAndGet();

        // sample time cost
        sample.stop(Timer.builder(CommonMetrics.GRPC_SERVER_DURATION.name())
                .tag("service", methodKey.service)
                .tag("method", methodKey.method)
                .tag("grpc_status", status.getCode().name())
                .description(CommonMetrics.GRPC_SERVER_DURATION.description())
                .register(meterRegistry)
        );
    }

    private Status statusFromThrowable(Throwable throwable) {
        return Status.fromThrowable(throwable);
    }

    private record MethodKey(String service, String method) {
        static MethodKey from(ServerCall<?, ?> call) {
            String fullName = call.getMethodDescriptor().getFullMethodName();
            int slash = fullName.lastIndexOf('/');
            if (slash < 0) {
                return new MethodKey("unknown", fullName);
            }
            return new MethodKey(fullName.substring(0, slash), fullName.substring(slash + 1));
        }
    }
}
