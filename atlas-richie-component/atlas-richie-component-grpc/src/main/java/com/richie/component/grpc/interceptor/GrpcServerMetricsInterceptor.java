/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.grpc.interceptor;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 服务端指标拦截器
 *
 * <p>基于 Micrometer 记录每次 gRPC 调用的请求数、延迟和错误数。
 * 指标按方法名和状态码自动分组。</p>
 *
 * <p>采集的指标：</p>
 * <ul>
 *   <li>{@code grpc.server.requests} — 请求总数（Tag: method, status）</li>
 *   <li>{@code grpc.server.request.duration} — 请求延迟（Tag: method, status）</li>
 *   <li>{@code grpc.server.responses.errors} — 错误数（Tag: method, status）</li>
 * </ul>
 *
 * <p>该拦截器需要处于调用链中靠近 Handler 的位置，以准确测量业务逻辑耗时。
 * 推荐注册顺序：Header → Logging → Auth → Metrics → Exception → Handler</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcServerMetricsInterceptor implements ServerInterceptor {

    private final MeterRegistry meterRegistry;

    public GrpcServerMetricsInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        var method = call.getMethodDescriptor().getFullMethodName();
        var sample = Timer.start(meterRegistry);
        var statusRef = new AtomicReference<>(Status.OK);

        var forwardCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                statusRef.set(status);
                super.close(status, trailers);
            }
        };

        var listener = next.startCall(forwardCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onComplete() {
                recordMetrics(method, statusRef.get(), sample);
            }

            @Override
            public void onCancel() {
                recordMetrics(method, Status.CANCELLED, sample);
            }
        };
    }

    private void recordMetrics(String method, Status status, Timer.Sample sample) {
        try {
            var statusTag = status.getCode().name();

            sample.stop(Timer.builder("grpc.server.request.duration")
                    .tag("method", method)
                    .tag("status", statusTag)
                    .description("gRPC server request duration")
                    .register(meterRegistry));

            Counter.builder("grpc.server.requests")
                    .tag("method", method)
                    .tag("status", statusTag)
                    .description("gRPC server request total count")
                    .register(meterRegistry)
                    .increment();

            if (!status.isOk()) {
                Counter.builder("grpc.server.responses.errors")
                        .tag("method", method)
                        .tag("status", statusTag)
                        .description("gRPC server error responses")
                        .register(meterRegistry)
                        .increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record gRPC metrics: method={}, status={}", method, status.getCode(), e);
        }
    }
}
