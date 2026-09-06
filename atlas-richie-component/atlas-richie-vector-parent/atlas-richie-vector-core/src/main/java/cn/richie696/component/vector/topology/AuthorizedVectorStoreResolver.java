/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.exceptions.VectorStoreAccessDeniedException;

import java.util.Objects;

/**
 * Resolves a Store only after an application-provided access decision.
 *
 * <p>This is a server-side composition API, not an HTTP routing endpoint. It keeps
 * physical provider resources behind the registered logical Store boundary.</p>
 */
public final class AuthorizedVectorStoreResolver {

    private final VectorServiceRegistry registry;
    private final VectorStoreAccessPolicy policy;

    public AuthorizedVectorStoreResolver(VectorServiceRegistry registry, VectorStoreAccessPolicy policy) {
        this.registry = Objects.requireNonNull(registry, "vector service registry must not be null");
        this.policy = Objects.requireNonNull(policy, "vector store access policy must not be null");
    }

    public VectorStoreHandle require(VectorStoreAccessRequest request) {
        Objects.requireNonNull(request, "vector store access request must not be null");
        VectorStoreHandle handle = registry.require(request.storeId());
        if (!policy.permits(request, handle.descriptor())) {
            throw new VectorStoreAccessDeniedException();
        }
        return handle;
    }
}
