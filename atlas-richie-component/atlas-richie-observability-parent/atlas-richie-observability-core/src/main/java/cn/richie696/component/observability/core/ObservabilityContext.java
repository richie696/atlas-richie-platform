/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.core;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

/**
 * 统一观测上下文的轻量入口。
 *
 * <p>OTel Context 负责技术调用链，request_id 作为独立业务关联字段存放在同一个 Context
 * 中。协议组件只需要捕获和恢复该 Context，不需要知道具体线程池或消息实现。</p>
 */
public final class ObservabilityContext {

    private static final ContextKey<String> REQUEST_ID =
            ContextKey.named("atlas-richie-observability.request-id");

    private ObservabilityContext() {
    }

    public static Context current() {
        return Context.current();
    }

    public static Scope makeCurrent(Context context) {
        return context == null ? Context.current().makeCurrent() : context.makeCurrent();
    }

    public static Scope withRequestId(String requestId) {
        return withRequestId(Context.current(), requestId).makeCurrent();
    }

    /**
     * Returns a context carrying the supplied business request ID.
     */
    public static Context withRequestId(Context context, String requestId) {
        Context base = context == null ? Context.current() : context;
        if (requestId == null || requestId.isBlank()) {
            return base;
        }
        return base.with(REQUEST_ID, requestId);
    }

    public static String currentRequestId() {
        return Context.current().get(REQUEST_ID);
    }

    public static CorrelationIds currentCorrelation() {
        return CorrelationIds.from(Span.current(), currentRequestId());
    }

    public static Context capture() {
        return Context.current();
    }
}
