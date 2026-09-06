/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/**
 * Application extension point for authorizing a caller against a logical Store.
 *
 * <p>It intentionally receives only logical Store metadata. Connection settings,
 * physical index names and credentials are never made available to the policy.</p>
 */
@FunctionalInterface
public interface VectorStoreAccessPolicy {

    boolean permits(VectorStoreAccessRequest request, VectorStoreDescriptor store);

    /**
     * Backward-compatible default: only explicit in-process composition requests
     * are accepted. Applications that need end-user authorization replace this bean.
     */
    static VectorStoreAccessPolicy internalOnly() {
        return (request, store) -> "internal".equals(request.callerType());
    }
}
