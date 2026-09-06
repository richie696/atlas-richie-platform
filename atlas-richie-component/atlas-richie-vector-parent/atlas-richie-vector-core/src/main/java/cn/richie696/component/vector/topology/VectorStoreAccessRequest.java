/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Map;
import java.util.Objects;

/**
 * Opaque caller context used by an application-defined Store access policy.
 *
 * <p>The vector component deliberately does not interpret identities, JWT claims,
 * tenants, roles or scopes. A consuming application may pass its already-verified
 * context here and decide access through {@link VectorStoreAccessPolicy}.</p>
 */
public record VectorStoreAccessRequest(
        String callerType,
        String operation,
        VectorStoreId storeId,
        Map<String, String> attributes) {

    public VectorStoreAccessRequest {
        if (callerType == null || callerType.isBlank()) {
            throw new IllegalArgumentException("vector store callerType must not be blank");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("vector store operation must not be blank");
        }
        Objects.requireNonNull(storeId, "vector store storeId must not be null");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    /** Creates a request for trusted, in-process composition code. */
    public static VectorStoreAccessRequest internal(String operation, VectorStoreId storeId) {
        return new VectorStoreAccessRequest("internal", operation, storeId, Map.of());
    }
}
