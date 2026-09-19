/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.core;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Objects;

/**
 * W3C/OTel carrier 传播适配器。
 *
 * <p>具体协议只负责提供 carrier 的 getter/setter；propagator 的具体实现由官方 OTel
 * Starter 或调用方提供，Core 不绑定 HTTP、gRPC、NATS 或 MCP SDK 类型。</p>
 */
public final class ObservabilityPropagator {

    private final TextMapPropagator delegate;

    public ObservabilityPropagator(TextMapPropagator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        delegate.inject(context == null ? Context.current() : context, carrier, setter);
    }

    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
        return delegate.extract(context == null ? Context.current() : context, carrier, getter);
    }

    public TextMapPropagator delegate() {
        return delegate;
    }
}
