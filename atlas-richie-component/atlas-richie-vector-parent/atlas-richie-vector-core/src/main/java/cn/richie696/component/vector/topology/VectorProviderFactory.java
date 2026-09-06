/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;

import java.util.Set;
/** Provider extension point for creating isolated connections and store-bound handles. */
public interface VectorProviderFactory {

    VectorProvider provider();

    /** Conservative known-native capability baseline; omission means unknown, not unsupported. */
    default VectorStoreCapabilities providerCapabilities() {
        return adapterCapabilities();
    }

    /** Capabilities implemented by this adapter version before Store-specific constraints. */
    default VectorStoreCapabilities adapterCapabilities() {
        return VectorStoreCapabilities.none();
    }

    /** Strictly validates provider-owned connection keys and value types without opening a resource. */
    void validateConnection(VectorConnectionDefinition definition);

    /** Validates store/index compatibility that can be established before opening a resource. */
    void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store);

    /** Capabilities expected for this exact configured Store and index declaration. */
    VectorStoreCapabilities capabilities(VectorConnectionDefinition connection, VectorStoreDefinition store);

    /**
     * Opaque identities of physical resources owned by this Store on the supplied connection.
     * Equal identities mean that two Stores would read or mutate the same provider resource.
     * Implementations must not include credentials; the values are used only for startup collision checks.
     */
    default Set<String> physicalResourceIdentities(
            VectorConnectionDefinition connection, VectorStoreDefinition store) {
        return Set.of();
    }

    VectorConnectionHandle openConnection(VectorConnectionDefinition definition);

    VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel);
}
