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

import java.util.Map;

/** Matches only when the Named topology declares exactly one logical Store. */
public final class SingleNamedVectorStoreCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, VectorProperties.StoreConfig> stores = Binder.get(context.getEnvironment())
                .bind(
                        "platform.component.vector.stores",
                        Bindable.mapOf(String.class, VectorProperties.StoreConfig.class))
                .orElse(Map.of());
        return stores.size() == 1;
    }
}
