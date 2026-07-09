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
package com.richie.component.redis.streammq.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Redis Stream OpenTelemetry 链路追踪工具类
 *
 * <p>提供无侵入的链路追踪功能，对业务代码完全透明：
 * <ul>
 *   <li><strong>自动注入</strong>：在消息发布时自动注入追踪上下文</li>
 *   <li><strong>自动提取</strong>：在消息消费时自动提取追踪上下文</li>
 *   <li><strong>透明处理</strong>：业务代码无需实现任何接口</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15
 */
@Component
@Slf4j
public class RedisStreamTracingUtils {

    /** 全局 OpenTelemetry 实例（init 后赋值） */
    private static OpenTelemetry openTelemetry;

    /** Redis Stream 使用的 Tracer */
    private static Tracer tracer;

    /** 应用内注入的 OpenTelemetry，作为 fallback */
    @Autowired
    private OpenTelemetry autowiredOpenTelemetry;

    /**
     * 初始化 OpenTelemetry 与 Tracer。
     * 优先使用 GlobalOpenTelemetry（Java Agent 配置），否则使用应用内注入的实例。
     */
    @PostConstruct
    public void init() {
        // 优先使用 GlobalOpenTelemetry（Java Agent 配置的），fallback 到应用内注入的实例
        openTelemetry = GlobalOpenTelemetry.get();
        if (openTelemetry == null) {
            openTelemetry = autowiredOpenTelemetry;
            log.debug("使用应用内 OpenTelemetry 实例");
        } else {
            log.debug("使用 GlobalOpenTelemetry 实例");
        }

        tracer = openTelemetry.getTracer("richie-redis-stream", "1.0.0");
        log.debug("OpenTelemetry Tracer for Redis Stream initialized with propagator: {}",
                openTelemetry.getPropagators().getTextMapPropagator().getClass().getSimpleName());
    }

    /**
     * 创建一个用于消息发布的 Span
     *
     * @param streamKey Stream 的键
     * @param messageType 消息类型
     * @return TracingScope 包含创建的 Span 和 Scope
     */
    public static TracingScope createPublisherSpan(String streamKey, String messageType) {
        if (tracer == null) return null;

        Span span = tracer.spanBuilder("redis.stream.publish")
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute("stream.key", streamKey)
                .setAttribute("message.type", messageType)
                .setAttribute("messaging.system", "redis")
                .setAttribute("messaging.destination", streamKey)
                .setAttribute("messaging.destination_kind", "stream")
                .setAttribute("messaging.operation", "publish")
                .startSpan();
        return new TracingScope(span);
    }

    /**
     * 创建一个用于消息消费的 Span，并继承上游上下文
     *
     * @param messageWrapper 可追踪的消息包装器
     * @param streamKey Stream 键
     * @param group 消费者组
     * @param operationName 操作名称
     * @return TracingScope 包含创建的 Span 和 Scope
     */
    public static TracingScope createConsumerSpan(TraceableMessageWrapper messageWrapper, String streamKey, String group, String operationName) {
        if (tracer == null || openTelemetry == null) return null;

        Context parentContext = messageWrapper.extractTraceContext(openTelemetry);
        Span span = tracer.spanBuilder(operationName)
                .setParent(parentContext)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("stream.key", streamKey)
                .setAttribute("consumer.group", group)
                .setAttribute("message.type", messageWrapper.originalMessage().getClass().getSimpleName())
                .setAttribute("messaging.system", "redis")
                .setAttribute("messaging.destination", streamKey)
                .setAttribute("messaging.destination_kind", "stream")
                .setAttribute("messaging.operation", "consume")
                .setAttribute("message.traceId", messageWrapper.getTraceId())
                .setAttribute("message.spanId", messageWrapper.getSpanId())
                .setAttribute("message.sampled", messageWrapper.isSampled())
                .startSpan();
        return new TracingScope(span);
    }

    /**
     * 创建一个新的 Span，不继承任何上游上下文
     *
     * @param operationName 操作名称
     * @return TracingScope 包含创建的 Span 和 Scope
     */
    public static TracingScope createNewSpan(String operationName) {
        if (tracer == null) return null;

        Span span = tracer.spanBuilder(operationName)
                .startSpan();
        return new TracingScope(span);
    }

    /**
     * 记录异常到 Span
     *
     * @param span 要记录的 Span
     * @param throwable 异常对象
     */
    public static void recordError(Span span, Throwable throwable) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR, throwable.getMessage());
            span.recordException(throwable);
        }
    }

    /**
     * 记录重试事件到 Span
     *
     * @param span 要记录的 Span
     * @param attempt 重试次数
     * @param maxAttempts 最大重试次数
     */
    public static void recordRetryEvent(Span span, int attempt, int maxAttempts) {
        if (span != null) {
            span.addEvent("retry.attempt",
                io.opentelemetry.api.common.Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.longKey("retry.attempt"), (long) attempt,
                    io.opentelemetry.api.common.AttributeKey.longKey("retry.max_attempts"), (long) maxAttempts
                ));
        }
    }

    /**
     * 记录消息处理成功事件到 Span
     *
     * @param span 要记录的 Span
     * @param processingTimeMs 处理时间（毫秒）
     */
    public static void recordSuccessEvent(Span span, long processingTimeMs) {
        if (span != null) {
            span.addEvent("message.processed.success",
                io.opentelemetry.api.common.Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.longKey("processing.time_ms"), processingTimeMs
                ));
        }
    }

    /**
     * 记录消息处理失败事件到 Span
     *
     * @param span 要记录的 Span
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     */
    public static void recordFailureEvent(Span span, String errorType, String errorMessage) {
        if (span != null) {
            span.addEvent("message.processed.failure",
                io.opentelemetry.api.common.Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.stringKey("error.type"), errorType,
                    io.opentelemetry.api.common.AttributeKey.stringKey("error.message"), errorMessage
                ));
        }
    }

    /**
     * 用于管理 Span 生命周期和 Scope 的辅助类
     */
    @Getter
    public static class TracingScope implements AutoCloseable {

        /** 当前 Span */
        private final Span span;

        /** 当前 Scope（用于 makeCurrent） */
        private final Scope scope;

        /**
         * 创建并激活当前 Span 的 Scope。
         *
         * @param span 要激活的 Span
         */
        public TracingScope(Span span) {
            this.span = span;
            this.scope = span.makeCurrent();
        }

        @Override
        public void close() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        }
    }
}
