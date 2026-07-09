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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 客户端指标拦截器
 *
 * <p>基于 Micrometer 记录每次 gRPC 客户端调用的请求数、延迟和错误数。</p>
 *
 * <p>采集的指标：</p>
 * <ul>
 *   <li>{@code grpc.client.requests} — 请求总数（Tag: method, status）</li>
 *   <li>{@code grpc.client.request.duration} — 请求延迟（Tag: method, status）</li>
 *   <li>{@code grpc.client.responses.errors} — 错误数（Tag: method, status）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcClientMetricsInterceptor implements ClientInterceptor {

    private final MeterRegistry meterRegistry;

    public GrpcClientMetricsInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        var fullMethodName = method.getFullMethodName();
        var sample = Timer.start(meterRegistry);
        var statusRef = new AtomicReference<>(Status.OK);

        var clientCall = next.newCall(method, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(clientCall) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                var forwardingListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        statusRef.set(status);
                        super.onClose(status, trailers);
                        recordMetrics(fullMethodName, status, sample);
                    }
                };
                super.start(forwardingListener, headers);
            }
        };
    }

    private void recordMetrics(String method, Status status, Timer.Sample sample) {
        try {
            var statusTag = status.getCode().name();

            sample.stop(Timer.builder("grpc.client.request.duration")
                    .tag("method", method)
                    .tag("status", statusTag)
                    .description("gRPC client request duration")
                    .register(meterRegistry));

            Counter.builder("grpc.client.requests")
                    .tag("method", method)
                    .tag("status", statusTag)
                    .description("gRPC client request total count")
                    .register(meterRegistry)
                    .increment();

            if (!status.isOk()) {
                Counter.builder("grpc.client.responses.errors")
                        .tag("method", method)
                        .tag("status", statusTag)
                        .description("gRPC client error responses")
                        .register(meterRegistry)
                        .increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record gRPC client metrics: method={}, status={}", method, status.getCode(), e);
        }
    }
}
