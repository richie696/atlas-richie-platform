/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 客户端 OpenTelemetry 链路追踪拦截器
 *
 * <p>为每个 gRPC 客户端调用创建 {@link SpanKind#CLIENT} span，通过 W3C 标准的 trace context 注入
 * 到请求 Metadata 中，使下游服务端能继承上游链路。同时将 traceId/spanId 写入 {@link MDC} 供日志关联。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * var interceptor = new GrpcClientTracingInterceptor();
 * var channel = ManagedChannelBuilder.forTarget("host:port")
 *     .intercept(interceptor)
 *     .build();
 * // ... 发起调用
 * interceptor.finishSpan(call, responseStatus);
 * }</pre>
 *
 * @author richie696
 * @since 1.0
 */
@Slf4j
public final class GrpcClientTracingInterceptor implements ClientInterceptor {

    /** 标记 RPC 系统类型 */
    private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    /** 标记 RPC 方法名 */
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
    /** 标记 gRPC 状态码 */
    private static final AttributeKey<Long> RPC_STATUS_CODE = AttributeKey.longKey("rpc.grpc.status_code");

    /** W3C 标准 TextMap 注入器，将 trace context 写入 gRPC Metadata */
    private static final TextMapSetter<Metadata> SETTER = (metadata, key, value) ->
            metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);

    private final Tracer tracer;

    /**
     * 使用 {@link GlobalOpenTelemetry} 创建拦截器
     */
    public GrpcClientTracingInterceptor() {
        this(GlobalOpenTelemetry.get());
    }

    /**
     * 使用自定义 {@link OpenTelemetry} 实例创建拦截器
     *
     * @param openTelemetry OpenTelemetry 实例
     */
    public GrpcClientTracingInterceptor(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("atlas-richie-grpc", "1.0.0");
    }

    /**
     * 拦截客户端 gRPC 调用，创建 CLIENT span 并注入 trace context
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>创建 CLIENT span 并设置 rpc.system / rpc.method 属性</li>
     *   <li>将 traceId/spanId 写入 {@link MDC} 供日志关联</li>
     *   <li>通过 W3C 标准将 trace context 注入请求 Metadata</li>
     *   <li>在 {@code start} 阶段发送请求，后续由 {@link #finishSpan(ClientCall, Status)} 结束 span</li>
     * </ol>
     *
     * @param method      RPC 方法描述符
     * @param callOptions 调用选项
     * @param next        下一个 Channel
     * @param <ReqT>      请求类型
     * @param <RespT>     响应类型
     * @return 包装后的 ClientCall，注入链路追踪 Metadata
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        var methodName = method.getFullMethodName();

        // 1. 创建 CLIENT span
        var span = tracer.spanBuilder(methodName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(RPC_SYSTEM, "grpc")
                .setAttribute(RPC_METHOD, methodName)
                .startSpan();

        // 2. 将 trace 信息注入 MDC 供日志关联
        var spanContext = span.getSpanContext();
        MDC.put("traceId", spanContext.getTraceId());
        MDC.put("spanId", spanContext.getSpanId());

        // 3. 设置当前 span 为 OTel Context 的活跃 span
        var otelContext = Context.current().with(span);
        var statusRef = new AtomicReference<>(Status.OK);

        // 4. 在 span scope 内创建 ClientCall
        ClientCall<ReqT, RespT> clientCall;
        try (Scope ignored = otelContext.makeCurrent()) {
            clientCall = next.newCall(method, callOptions);
        }

        return new ForwardingClientCall.SimpleForwardingClientCall<>(clientCall) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 5. 通过 W3C propagator 将 trace context 注入请求 Metadata
                GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                        .inject(otelContext, headers, SETTER);

                // 6. 包装响应监听器，在 onClose 时捕获最终状态码（由 finishSpan 消费）
                var tracedListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        statusRef.set(status);
                        super.onClose(status, trailers);
                    }
                };

                super.start(tracedListener, headers);
            }
        };
    }

    /**
     * 结束 CLIENT span，写入最终状态码并清理 MDC
     *
     * <p>应在获取到服务端响应后调用，以确保 span 能正确记录 gRPC 状态码。
     * 如果 span 已失效（如已完成），则直接返回。</p>
     *
     * @param call    ClientCall 实例
     * @param status  gRPC 最终状态
     * @param <ReqT>  请求类型
     * @param <RespT> 响应类型
     */
    public <ReqT, RespT> void finishSpan(ClientCall<ReqT, RespT> call, Status status) {
        Span current = Span.current();
        if (current == Span.getInvalid()) {
            return;
        }
        try {
            current.setAttribute(RPC_STATUS_CODE, (long) status.getCode().value());
            if (!status.isOk()) {
                current.setStatus(StatusCode.ERROR, status.getDescription());
            }
        } finally {
            current.end();
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }
}
