/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/** Resolves an exact configured model bean name without guessing or fallback. */
@FunctionalInterface
public interface NamedEmbeddingModelResolver {

    VectorEmbeddingModelBinding resolve(String beanName);
}
