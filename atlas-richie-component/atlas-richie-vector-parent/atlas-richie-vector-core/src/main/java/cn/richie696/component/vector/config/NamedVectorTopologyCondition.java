/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Matches only when at least one named topology collection is configured. */
public final class NamedVectorTopologyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Binder binder = Binder.get(context.getEnvironment());
        return binder.bind(
                        "platform.component.vector.connections",
                        Bindable.mapOf(String.class, VectorProperties.ConnectionConfig.class)).isBound()
                || binder.bind(
                        "platform.component.vector.stores",
                        Bindable.mapOf(String.class, VectorProperties.StoreConfig.class)).isBound();
    }
}
