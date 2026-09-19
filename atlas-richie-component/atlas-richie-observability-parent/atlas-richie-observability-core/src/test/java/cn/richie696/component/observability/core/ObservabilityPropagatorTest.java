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
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityPropagatorTest {

    @Test
    void delegatesInjectExtractAndUsesCurrentContextForNullInputs() {
        AtomicReference<Context> injected = new AtomicReference<>();
        TextMapPropagator delegate = new TextMapPropagator() {
            @Override
            public Collection<String> fields() {
                return List.of("traceparent");
            }

            @Override
            public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
                injected.set(context);
                setter.set(carrier, "traceparent", "00-test");
            }

            @Override
            public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
                return context;
            }
        };
        ObservabilityPropagator propagator = new ObservabilityPropagator(delegate);
        AtomicReference<String> carrier = new AtomicReference<>();
        propagator.inject(null, carrier, (target, key, value) -> target.set(value));
        assertThat(carrier).hasValue("00-test");
        assertThat(injected).hasValue(Context.current());
        TextMapGetter<AtomicReference<String>> getter = new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(AtomicReference<String> target) {
                return Collections.singleton("traceparent");
            }

            @Override
            public String get(AtomicReference<String> target, String key) {
                return target.get();
            }
        };
        assertThat(propagator.extract(null, carrier, getter)).isNotNull();
        assertThat(propagator.delegate()).isSameAs(delegate);
    }
}
