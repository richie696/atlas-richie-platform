package com.richie.component.redis.streammq.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

/**
 * 可追踪消息包装器
 *
 * <p>用于在 Redis Stream 消息中自动注入和提取链路追踪上下文，
 * 对业务代码完全透明，无需业务消息类实现任何接口。
 *
 * <p>主要功能：
 * <ul>
 *   <li>封装原始业务消息与追踪上下文</li>
 *   <li>发布端注入 traceparent/tracestate 与便于调试的 traceId/spanId/sample</li>
 *   <li>消费端提取上下文并恢复父子关系</li>
 *   <li>兼容 Java Agent 提供的 GlobalOpenTelemetry 或应用内 OpenTelemetry</li>
 * </ul>
 *
 * @param originalMessage 原始业务消息
 * @param traceContext    链路追踪上下文（traceId、spanId、sampled 等）
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15
 */
@Slf4j
public record TraceableMessageWrapper(Object originalMessage, Map<String, String> traceContext) {

    // 链路追踪字段键名
    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String SAMPLED_KEY = "sampled";

    /**
     * 构造函数
     *
     * @param originalMessage 原始业务消息
     */
    public TraceableMessageWrapper(Object originalMessage) {
        this(originalMessage, new HashMap<>());
    }

    /**
     * 构造函数（带追踪上下文）
     *
     * @param originalMessage 原始业务消息
     * @param traceContext    追踪上下文
     */
    public TraceableMessageWrapper(Object originalMessage, Map<String, String> traceContext) {
        this.originalMessage = originalMessage;
        this.traceContext = traceContext != null ? new HashMap<>(traceContext) : new HashMap<>();
    }

    /**
     * 从当前 OpenTelemetry 上下文注入追踪信息
     *
     * @param openTelemetry OpenTelemetry 实例（可选，优先使用 GlobalOpenTelemetry）
     */
    public void injectTraceContext(OpenTelemetry openTelemetry) {
        try {
            // 获取当前 Span 上下文
            SpanContext spanContext = Span.current().getSpanContext();
            if (!spanContext.isValid()) {
                log.debug("当前没有有效的 Span 上下文，跳过注入");
                return;
            }

            // 优先使用 GlobalOpenTelemetry（Java Agent 配置的），fallback 到传入的实例
            OpenTelemetry otel = GlobalOpenTelemetry.get();
            if (otel == null || otel.getPropagators().getTextMapPropagator().getClass().getSimpleName().contains("Noop")) {
                otel = openTelemetry;
                log.debug("使用应用内 OpenTelemetry 实例");
            } else {
                log.debug("使用 GlobalOpenTelemetry 实例");
            }

            // 使用 TextMapPropagator 注入追踪上下文
            TextMapPropagator propagator = otel.getPropagators().getTextMapPropagator();
            log.debug("使用的传播器类型: {}", propagator.getClass().getSimpleName());

            propagator.inject(Context.current(), traceContext, (carrier, key, value) -> {
                if (carrier != null) {
                    carrier.put(key, value);
                }
            });

            // 手动添加便于调试和兼容的字段
            traceContext.put(TRACE_ID_KEY, spanContext.getTraceId());
            traceContext.put(SPAN_ID_KEY, spanContext.getSpanId());
            traceContext.put(SAMPLED_KEY, String.valueOf(spanContext.isSampled()));

            log.debug("注入追踪上下文: traceId={}, spanId={}, sampled={}, propagator={}",
                    spanContext.getTraceId(), spanContext.getSpanId(), spanContext.isSampled(),
                    propagator.getClass().getSimpleName());

        } catch (Exception e) {
            log.warn("注入追踪上下文失败", e);
        }
    }

    /**
     * 从消息中提取追踪上下文
     *
     * @param openTelemetry OpenTelemetry 实例（可选，优先使用 GlobalOpenTelemetry）
     * @return 包含追踪上下文的 OpenTelemetry Context
     */
    public Context extractTraceContext(OpenTelemetry openTelemetry) {
        try {
            if (traceContext.isEmpty()) {
                log.debug("消息中没有追踪上下文，返回当前上下文");
                return Context.current();
            }

            // 优先使用 GlobalOpenTelemetry（Java Agent 配置的），fallback 到传入的实例
            OpenTelemetry otel = GlobalOpenTelemetry.get();
            if (otel == null || otel.getPropagators().getTextMapPropagator().getClass().getSimpleName().contains("Noop")) {
                otel = openTelemetry;
                log.debug("使用应用内 OpenTelemetry 实例进行提取");
            } else {
                log.debug("使用 GlobalOpenTelemetry 实例进行提取");
            }

            // 使用 TextMapPropagator 提取上下文
            TextMapPropagator propagator = otel.getPropagators().getTextMapPropagator();
            log.debug("使用的传播器类型: {}", propagator.getClass().getSimpleName());

            return propagator.extract(Context.current(), traceContext, new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(@Nonnull Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, @Nonnull String key) {
                    return carrier.get(key);
                }
            });

        } catch (Exception e) {
            log.warn("提取追踪上下文失败", e);
            return Context.current();
        }
    }

    /**
     * 检查是否有有效的追踪上下文
     *
     * @return true 如果有有效的追踪上下文
     */
    public boolean hasValidTraceContext() {
        // 兼容两类判定：
        // 1) 标准 OTel 传播载体：traceparent/tracestate（优先）
        // 2) 冗余简化字段：仅 traceId（用于日志排查）
        if (traceContext == null || traceContext.isEmpty()) {
            return false;
        }
        if (traceContext.containsKey("traceparent")) {
            return true;
        }
        String tid = traceContext.get(TRACE_ID_KEY);
        return tid != null && !tid.isEmpty();
    }

    /**
     * 获取 Trace ID
     *
     * @return Trace ID，如果没有则返回 null
     */
    public String getTraceId() {
        return traceContext.get(TRACE_ID_KEY);
    }

    /**
     * 获取 Span ID
     *
     * @return Span ID，如果没有则返回 null
     */
    public String getSpanId() {
        return traceContext.get(SPAN_ID_KEY);
    }

    /**
     * 检查是否被采样
     *
     * @return true 如果被采样，否则 false
     */
    public boolean isSampled() {
        String sampled = traceContext.get(SAMPLED_KEY);
        return "true".equalsIgnoreCase(sampled);
    }

    /**
     * 获取追踪上下文映射
     *
     * @return 追踪上下文映射
     */
    @Override
    public Map<String, String> traceContext() {
        return new HashMap<>(traceContext);
    }

    /**
     * 创建消息包装器（用于发布）
     *
     * @param message       业务消息
     * @param openTelemetry OpenTelemetry 实例
     * @return 包装后的消息
     */
    public static TraceableMessageWrapper wrapForPublish(Object message, OpenTelemetry openTelemetry) {
        TraceableMessageWrapper wrapper = new TraceableMessageWrapper(message);
        wrapper.injectTraceContext(openTelemetry);
        return wrapper;
    }

    /**
     * 创建消息包装器（用于消费）
     *
     * @param message      业务消息
     * @param traceContext 追踪上下文
     * @return 包装后的消息
     */
    public static TraceableMessageWrapper wrapForConsume(Object message, Map<String, String> traceContext) {
        return new TraceableMessageWrapper(message, traceContext);
    }

    @Nonnull
    @Override
    public String toString() {
        return String.format("TraceableMessageWrapper{originalMessage=%s, traceId=%s, spanId=%s, sampled=%s}",
                originalMessage.getClass().getSimpleName(), getTraceId(), getSpanId(), isSampled());
    }
}
