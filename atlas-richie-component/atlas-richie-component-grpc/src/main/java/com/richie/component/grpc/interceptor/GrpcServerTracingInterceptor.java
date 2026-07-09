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
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 服务端 OpenTelemetry 链路追踪拦截器
 *
 * <p>为每个 gRPC 调用创建 {@link SpanKind#SERVER} span，自动提取上游 trace context（W3C traceparent 或 x-trace-id），
 * 将 spanId/traceId 注入 {@link MDC} 供日志关联，并将 traceId 写入响应 Metadata 透传给调用方。</p>
 *
 * <p>span 属性：</p>
 * <ul>
 *   <li>{@code rpc.system} — 固定值 "grpc"</li>
 *   <li>{@code rpc.method} — gRPC 方法全名</li>
 *   <li>{@code rpc.grpc.status_code} — gRPC 状态码</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0
 */
@Slf4j
public final class GrpcServerTracingInterceptor implements ServerInterceptor {

    /** 标记 RPC 系统类型 */
    private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    /** 标记 RPC 方法名 */
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
    /** 标记 gRPC 状态码 */
    private static final AttributeKey<Long> RPC_STATUS_CODE = AttributeKey.longKey("rpc.grpc.status_code");

    /** 响应中携带 traceId 的 Metadata key */
    private static final Metadata.Key<String> TRACE_ID_HEADER =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    /** W3C 标准 TextMap 提取器，从 gRPC Metadata 中提取上游 trace context */
    private static final TextMapGetter<Metadata> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Metadata metadata) {
            return metadata.keys();
        }

        @Override
        public String get(Metadata metadata, String key) {
            return metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        }
    };

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    /**
     * 使用 {@link GlobalOpenTelemetry} 创建拦截器
     */
    public GrpcServerTracingInterceptor() {
        this(GlobalOpenTelemetry.get());
    }

    /**
     * 使用自定义 {@link OpenTelemetry} 实例创建拦截器
     *
     * @param openTelemetry OpenTelemetry 实例
     */
    public GrpcServerTracingInterceptor(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("atlas-richie-grpc", "1.0.0");
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * 拦截服务端 gRPC 调用，创建 SERVER span 并管理完整生命周期
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>从请求 Metadata 提取上游 trace context（W3C traceparent）</li>
     *   <li>创建 SERVER span 并设置 OTel Context</li>
     *   <li>将 traceId/spanId 写入 {@link MDC} 供日志关联</li>
     *   <li>委托 Handler 执行业务逻辑</li>
     *   <li>业务异常时通过 {@link Span#recordException(Throwable)} 记录</li>
     *   <li>调用完成/取消时结束 span，写入状态码，清理 MDC</li>
     * </ol>
     *
     * @param call    gRPC 服务端调用
     * @param headers 请求 Metadata
     * @param next    下一个 Handler（实际的业务实现或下一个拦截器）
     * @param <ReqT>  请求类型
     * @param <RespT> 响应类型
     * @return 包装后的 ServerCall.Listener
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        var methodName = call.getMethodDescriptor().getFullMethodName();

        // 1. 从请求 Metadata 提取上游 trace context
        var extracted = propagator.extract(Context.current(), headers, GETTER);

        // 2. 创建 SERVER span
        var span = tracer.spanBuilder(methodName)
                .setParent(extracted)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(RPC_SYSTEM, "grpc")
                .setAttribute(RPC_METHOD, methodName)
                .startSpan();

        // 3. 将 trace 信息注入 MDC，供 Slf4j 日志自动关联
        var spanContext = span.getSpanContext();
        MDC.put("traceId", spanContext.getTraceId());
        MDC.put("spanId", spanContext.getSpanId());

        // 4. 设置当前 span 为 OTel Context 的活跃 span
        Context otelContext = Context.current().with(span);
        var statusRef = new AtomicReference<>(Status.OK);

        // 包装 ServerCall，拦截 close 以捕获最终状态码
        var tracedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                statusRef.set(status);
                super.close(status, trailers);
            }
        };

        // 5. 委托 Handler 执行业务逻辑（在 span scope 内）
        ServerCall.Listener<ReqT> listener;
        try (Scope ignored = otelContext.makeCurrent()) {
            listener = next.startCall(tracedCall, headers);
        }

        // 6. 包装 Listener，管理 span 生命周期
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onMessage(ReqT message) {
                try (Scope ignored = otelContext.makeCurrent()) {
                    super.onMessage(message);
                }
            }

            @Override
            public void onHalfClose() {
                try (Scope ignored = otelContext.makeCurrent()) {
                    super.onHalfClose();
                } catch (Exception e) {
                    // 业务异常记录到 span，不阻断传播（由 ExceptionInterceptor 进一步处理）
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    throw e;
                }
            }

            @Override
            public void onComplete() {
                try (Scope ignored = otelContext.makeCurrent()) {
                    super.onComplete();
                } finally {
                    finishSpan(span, statusRef.get(), headers);
                }
            }

            @Override
            public void onCancel() {
                try (Scope ignored = otelContext.makeCurrent()) {
                    super.onCancel();
                } finally {
                    finishSpan(span, Status.CANCELLED, headers);
                }
            }

            @Override
            public void onReady() {
                try (Scope ignored = otelContext.makeCurrent()) {
                    super.onReady();
                }
            }
        };
    }

    /**
     * 结束 span：写入最终状态码和 traceId，清理 MDC
     *
     * @param span           当前 span
     * @param status         gRPC 最终状态
     * @param responseHeaders 响应 Metadata（用于写入 traceId 给调用方）
     */
    private void finishSpan(Span span, Status status, Metadata responseHeaders) {
        try {
            span.setAttribute(RPC_STATUS_CODE, (long) status.getCode().value());
            if (status.isOk()) {
                span.setStatus(StatusCode.OK);
            } else {
                span.setStatus(StatusCode.ERROR, status.getDescription());
            }
            // 将 traceId 写入响应头，方便调用方关联日志
            responseHeaders.put(TRACE_ID_HEADER, span.getSpanContext().getTraceId());
        } finally {
            span.end();
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }
}
