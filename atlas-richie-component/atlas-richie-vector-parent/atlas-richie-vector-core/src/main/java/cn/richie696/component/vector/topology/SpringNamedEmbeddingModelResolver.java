/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.Objects;

/** Spring bean-name based resolver used by named topology assembly. */
public final class SpringNamedEmbeddingModelResolver implements NamedEmbeddingModelResolver {

    private final ListableBeanFactory beanFactory;

    public SpringNamedEmbeddingModelResolver(ListableBeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory must not be null");
    }

    @Override
    public VectorEmbeddingModelBinding resolve(String beanName) {
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalArgumentException("embedding model bean name must not be blank");
        }
        if (!beanFactory.containsBean(beanName)) {
            throw new IllegalArgumentException("embedding model bean is not available: " + beanName);
        }
        EmbeddingModel model = beanFactory.getBean(beanName, EmbeddingModel.class);
        return VectorEmbeddingModelBinding.of(beanName, model);
    }
}
