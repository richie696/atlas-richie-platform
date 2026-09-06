/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorStoreId;

import java.util.List;
import java.util.Objects;

/**
 * Sanitized proof of which advanced query options reached a Store adapter.
 * It deliberately excludes query text, vectors, filters, credentials and physical targets.
 */
public record VectorSearchExecutionReceipt(
        VectorStoreId storeId,
        VectorProvider provider,
        String contractVersion,
        String adapterVersion,
        String capabilityVersion,
        String providerExtensionVersion,
        String logicalIndexFingerprint,
        List<VectorParameterEvidence> parameters) {

    public VectorSearchExecutionReceipt(
            VectorStoreId storeId,
            VectorProvider provider,
            String contractVersion,
            String providerExtensionVersion,
            String logicalIndexFingerprint,
            List<VectorParameterEvidence> parameters) {
        this(storeId, provider, contractVersion, "1.0", "1.0", providerExtensionVersion,
                logicalIndexFingerprint, parameters);
    }

    public VectorSearchExecutionReceipt {
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        requireText(contractVersion, "contractVersion");
        requireText(adapterVersion, "adapterVersion");
        requireText(capabilityVersion, "capabilityVersion");
        requireText(providerExtensionVersion, "providerExtensionVersion");
        requireText(logicalIndexFingerprint, "logicalIndexFingerprint");
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
