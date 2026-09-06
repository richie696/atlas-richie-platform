/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service;

import java.util.Map;

/**
 * Optional capability for managing secondary indexes on a vector index's payload
 * (e.g. Qdrant payload indexes, Milvus scalar field indexes).
 *
 * <p>Payload indexes do not change the vector recall path; they accelerate filter
 * predicates that providers would otherwise scan linearly. They are not
 * {@link #createIndex index creation} and must be applied as a separate
 * configuration layer or via an explicit lifecycle call.</p>
 *
 * <p>Implementations MUST NOT log credential values, raw query bodies or full
 * ACL subject collections when reporting payload-index creation failures.
 * Provider-specific fields (Qdrant field type, schema identifier, ...) are
 * expressed through the opaque {@link FieldDefinition#settings} map so the
 * framework contract stays provider-neutral.</p>
 */
public interface VectorPayloadIndexOperations {

    /** Field type discriminator the provider expects; provider-specific names allowed. */
    enum FieldType { KEYWORD, INTEGER, FLOAT, GEO, BOOL, TEXT, DATETIME }

    /**
     * One declared payload field that requires a provider-side secondary index.
     *
     * @param field    logical field name; provider-specific charset rules apply
     * @param type     required type the provider must materialize
     * @param settings provider-owned opaque settings (e.g. Qdrant tokenizer,
     *                 on-disk flag); MUST NOT carry credentials or connection data
     */
    record FieldDefinition(String field, FieldType type, Map<String, String> settings) {
        public FieldDefinition {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("payload field name must not be blank");
            }
            if (type == null) {
                throw new IllegalArgumentException("payload field type must not be null");
            }
            settings = settings == null ? Map.of() : Map.copyOf(settings);
        }
    }

    /**
     * Create the given payload fields as secondary indexes. Implementations
     * MUST treat this as idempotent: already-existing fields with the same
     * declared type are reported as success.
     *
     * @param indexName the bound logical or physical index name
     * @param fields    one or more fields; empty list is a no-op
     * @return number of fields that were actually created (excluding pre-existing ones)
     */
    int createPayloadIndexes(String indexName, java.util.List<FieldDefinition> fields);

    /**
     * Drop the given payload fields. Implementations MUST be tolerant of
     * missing fields.
     *
     * @param indexName the bound logical or physical index name
     * @param fields    field names to drop; empty list is a no-op
     * @return number of fields that were actually dropped
     */
    int dropPayloadIndexes(String indexName, java.util.List<String> fields);
}
