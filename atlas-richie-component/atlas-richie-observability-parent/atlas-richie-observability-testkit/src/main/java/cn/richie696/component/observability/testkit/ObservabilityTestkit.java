/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.testkit;

import cn.richie696.component.observability.core.CorrelationIds;
import cn.richie696.component.observability.core.ObservabilityState;

/**
 * 可观测性组件契约测试的无框架断言。
 *
 * <p>测试工具只表达平台契约，不绑定某个应用的 HTTP、消息或数据库实现。</p>
 */
public final class ObservabilityTestkit {

    private ObservabilityTestkit() {
    }

    public static void assertEnabled(ObservabilityState state) {
        if (state == null || state.disabled()) {
            throw new AssertionError("observability must be enabled");
        }
    }

    public static void assertDisabled(ObservabilityState state) {
        if (state == null || state.enabled()) {
            throw new AssertionError("observability must be disabled");
        }
    }

    public static void assertIndependentIds(CorrelationIds ids) {
        if (ids == null) {
            throw new AssertionError("correlation ids must not be null");
        }
        if (!ids.requestId().isBlank()
                && (ids.requestId().equals(ids.traceId()) || ids.requestId().equals(ids.spanId()))) {
            throw new AssertionError("request_id must not replace trace_id or span_id");
        }
    }
}
