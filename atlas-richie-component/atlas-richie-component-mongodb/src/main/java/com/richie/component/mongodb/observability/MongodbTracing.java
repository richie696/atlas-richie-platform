package com.richie.component.mongodb.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MongoDB OpenTelemetry 链路追踪工具类
 *
 * <p>提供 MongoDB 操作的链路追踪功能：
 * <ul>
 *   <li><strong>Span 创建</strong>：为每个 MongoDB 操作创建独立的 Span</li>
 *   <li><strong>属性设置</strong>：设置 db.system、db.operation、db.mongodb.collection、db.statement 等标准属性</li>
 *   <li><strong>错误记录</strong>：发生异常时记录错误状态和异常信息</li>
 *   <li><strong>嵌套 Scope</strong>：通过 TracingScope 实现 AutoCloseable 自动管理生命周期</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-12-01
 */
@Component
@Slf4j
public class MongodbTracing {

    /** 全局 OpenTelemetry 实例（init 后赋值） */
    private static OpenTelemetry openTelemetry;

    /** MongoDB 使用的 Tracer */
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
        openTelemetry = GlobalOpenTelemetry.get();
        if (openTelemetry == null) {
            openTelemetry = autowiredOpenTelemetry;
            log.debug("使用应用内 OpenTelemetry 实例");
        } else {
            log.debug("使用 GlobalOpenTelemetry 实例");
        }

        tracer = openTelemetry.getTracer("richie-mongodb", "1.0.0");
        log.debug("OpenTelemetry Tracer for MongoDB initialized");
    }

    /**
     * 创建一个 MongoDB 操作的 Span
     *
     * @param operation 操作类型（find/insert/update/delete/count/save/drop）
     * @param collection 集合名称
     * @param statement 查询语句（截断至 1024 字符）
     * @return TracingScope 包含创建的 Span 和 Scope
     */
    public static TracingScope createSpan(String operation, String collection, String statement) {
        if (tracer == null) return null;

        String truncatedStatement = truncateStatement(statement);
        Span span = tracer.spanBuilder("mongodb." + operation)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("db.system", "mongodb")
                .setAttribute("db.operation", operation)
                .setAttribute("db.mongodb.collection", collection)
                .setAttribute("db.statement", truncatedStatement)
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
     * 记录成功状态到 Span
     *
     * @param span 要记录的 Span
     * @param durationMs 操作耗时（毫秒）
     */
    public static void recordSuccess(Span span, long durationMs) {
        if (span != null) {
            span.setAttribute("db.operation.duration_ms", durationMs);
        }
    }

    /**
     * 截断语句至指定长度
     *
     * @param statement 原始语句
     * @return 截断后的语句
     */
    private static String truncateStatement(String statement) {
        if (statement == null) return "";
        if (statement.length() <= 1024) return statement;
        return statement.substring(0, 1024) + "...";
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
         * 创建并激活当前 Span 的 Scope
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
