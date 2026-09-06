/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.exceptions.VectorCapabilityMismatchException;
import cn.richie696.component.vector.observation.ObservedVectorService;
import cn.richie696.component.vector.observation.ObservedVectorAdvancedSearchOperations;
import cn.richie696.component.vector.observation.ObservedVectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.observation.VectorStoreObservationHook;
import cn.richie696.component.vector.service.VectorService;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Store-bound runtime handle. The mandatory {@link VectorService} keeps simple
 * usage small; advanced operations are discoverable as typed optional capabilities.
 */
public final class VectorStoreHandle {

    private final VectorStoreDefinition definition;
    private final VectorProvider provider;
    private final VectorService service;
    private final VectorService capabilitySource;
    private final String embeddingModelFingerprint;
    private final VectorStoreCapabilities storeCapabilities;
    private final Map<Class<?>, Object> capabilities;

    private VectorStoreHandle(Builder builder) {
        this.definition = builder.definition;
        this.provider = builder.provider;
        this.service = builder.service;
        this.capabilitySource = builder.capabilitySource;
        this.embeddingModelFingerprint = builder.embeddingModelFingerprint;
        this.storeCapabilities = builder.storeCapabilities;
        this.capabilities = Collections.unmodifiableMap(new LinkedHashMap<>(builder.capabilities));
    }

    public static Builder builder(VectorStoreDefinition definition, VectorProvider provider, VectorService service) {
        return new Builder(definition, provider, service);
    }

    public VectorStoreDefinition definition() {
        return definition;
    }

    public VectorStoreId id() {
        return definition.id();
    }

    public VectorProvider provider() {
        return provider;
    }

    public VectorService service() {
        return service;
    }

    public String embeddingModelFingerprint() {
        return embeddingModelFingerprint;
    }

    public VectorStoreCapabilities storeCapabilities() {
        return storeCapabilities;
    }

    public <T> Optional<T> capability(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType must not be null");
        Object explicit = capabilities.get(capabilityType);
        if (explicit != null) {
            return Optional.of(capabilityType.cast(explicit));
        }
        if (capabilityType.isInstance(service)) {
            return Optional.of(capabilityType.cast(service));
        }
        return capabilityType.isInstance(capabilitySource)
                ? Optional.of(capabilityType.cast(capabilitySource))
                : Optional.empty();
    }

    public <T> T requireCapability(Class<T> capabilityType) {
        return capability(capabilityType).orElseThrow(() -> new VectorCapabilityMismatchException(
                "vector store " + id() + " does not expose capability " + capabilityType.getName()));
    }

    public VectorStoreDescriptor descriptor() {
        Set<String> exposedTypes = capabilities.keySet().stream()
                .map(Class::getName)
                .collect(Collectors.toUnmodifiableSet());
        return new VectorStoreDescriptor(
                definition.id(),
                definition.connectionId(),
                provider,
                definition.required(),
                embeddingModelFingerprint,
                definition.embeddingNormalization(),
                definition.embeddingModalities(),
                definition.requiredCapabilities(),
                exposedTypes,
                storeCapabilities.ids());
    }

    VectorStoreHandle observed(VectorStoreObservationHook hook) {
        Objects.requireNonNull(hook, "hook must not be null");
        Builder builder = builder(
                definition,
                provider,
                new ObservedVectorService(service, id(), provider, storeCapabilities.ids(), hook));
        builder.capabilitySource = capabilitySource;
        builder.embeddingModelFingerprint = embeddingModelFingerprint;
        builder.storeCapabilities = storeCapabilities;
        capabilities.forEach((type, implementation) -> builder.capabilities.put(
                type, observedCapability(type, implementation, hook)));
        return builder.build();
    }

    private Object observedCapability(Class<?> type, Object implementation, VectorStoreObservationHook hook) {
        if (type == VectorAdvancedSearchOperations.class) {
            return new ObservedVectorAdvancedSearchOperations(
                    (VectorAdvancedSearchOperations) implementation,
                    id(), provider, definition.defaultIndex(), storeCapabilities.ids(), hook);
        }
        if (type == VectorAclAwareHybridSearchOperations.class) {
            return new ObservedVectorAclAwareHybridSearchOperations(
                    (VectorAclAwareHybridSearchOperations) implementation,
                    id(), provider, storeCapabilities.ids(), hook);
        }
        return implementation;
    }

    public static final class Builder {

        private final VectorStoreDefinition definition;
        private final VectorProvider provider;
        private final VectorService service;
        private VectorService capabilitySource;
        private final Map<Class<?>, Object> capabilities = new LinkedHashMap<>();
        private String embeddingModelFingerprint = "unresolved";
        private VectorStoreCapabilities storeCapabilities = VectorStoreCapabilities.none();

        private Builder(VectorStoreDefinition definition, VectorProvider provider, VectorService service) {
            this.definition = Objects.requireNonNull(definition, "definition must not be null");
            this.provider = Objects.requireNonNull(provider, "provider must not be null");
            this.service = Objects.requireNonNull(service, "service must not be null");
            this.capabilitySource = service;
        }

        public <T> Builder capability(Class<T> capabilityType, T implementation) {
            Objects.requireNonNull(capabilityType, "capabilityType must not be null");
            Objects.requireNonNull(implementation, "implementation must not be null");
            if (!capabilityType.isInstance(implementation)) {
                throw new IllegalArgumentException(implementation.getClass().getName()
                        + " does not implement " + capabilityType.getName());
            }
            if (capabilities.putIfAbsent(capabilityType, implementation) != null) {
                throw new IllegalArgumentException("duplicate vector capability: " + capabilityType.getName());
            }
            return this;
        }

        public Builder embeddingModel(VectorEmbeddingModelBinding binding) {
            this.embeddingModelFingerprint = Objects.requireNonNull(binding, "binding must not be null").fingerprint();
            return this;
        }

        public Builder storeCapabilities(VectorStoreCapabilities storeCapabilities) {
            this.storeCapabilities = Objects.requireNonNull(storeCapabilities, "storeCapabilities must not be null");
            return this;
        }

        public VectorStoreHandle build() {
            return new VectorStoreHandle(this);
        }
    }
}
