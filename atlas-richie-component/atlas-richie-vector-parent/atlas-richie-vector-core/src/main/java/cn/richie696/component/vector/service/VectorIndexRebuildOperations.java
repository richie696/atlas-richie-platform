package cn.richie696.component.vector.service;

import cn.richie696.component.vector.config.VectorProperties;

/**
 * Provider-specific index rebuild capability used by blue/green RAG rebuilds.
 *
 * <p>A rebuild creates and prepares the target physical index. The application
 * layer remains responsible for reading the source of truth, re-embedding
 * records and writing them to the target index; this keeps embedding/model
 * concerns out of vector providers while making the physical index operation
 * provider-owned.</p>
 *
 * <p>Providers that cannot create an isolated target index must not implement
 * this capability. Callers should check the capability before invoking it and
 * surface a clear unsupported-provider error.</p>
 */
public interface VectorIndexRebuildOperations {

    /**
     * Prepare an isolated target index for a rebuild.
     *
     * @param sourceIndexName current physical index; used for diagnostics and
     *                        provider validation where applicable
     * @param targetIndexName new physical index that will receive rebuilt data
     * @param config          target index configuration
     * @throws IllegalArgumentException if names/configuration are invalid
     * @throws IllegalStateException if the provider cannot prepare the target
     */
    void rebuildIndex(String sourceIndexName,
                      String targetIndexName,
                      VectorProperties.IndexConfig config);
}
