/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.observation.VectorStoreObservationHook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral startup assembler for a fixed named topology. */
public final class VectorTopologyBootstrap {

    private VectorTopologyBootstrap() {
    }

    public static VectorTopologyRuntime start(
            Collection<VectorConnectionDefinition> connectionDefinitions,
            Collection<VectorStoreDefinition> storeDefinitions,
            Collection<VectorProviderFactory> providerFactories,
            NamedEmbeddingModelResolver embeddingModelResolver) {
        return start(connectionDefinitions, storeDefinitions, providerFactories, embeddingModelResolver, null);
    }

    public static VectorTopologyRuntime start(
            Collection<VectorConnectionDefinition> connectionDefinitions,
            Collection<VectorStoreDefinition> storeDefinitions,
            Collection<VectorProviderFactory> providerFactories,
            NamedEmbeddingModelResolver embeddingModelResolver,
            VectorStoreObservationHook observationHook) {
        Objects.requireNonNull(connectionDefinitions, "connectionDefinitions must not be null");
        Objects.requireNonNull(storeDefinitions, "storeDefinitions must not be null");
        Objects.requireNonNull(embeddingModelResolver, "embeddingModelResolver must not be null");

        Map<VectorConnectionId, VectorConnectionDefinition> connections = indexConnections(connectionDefinitions);
        DefaultVectorConnectionRegistry connectionRegistry = new DefaultVectorConnectionRegistry(providerFactories);
        List<VectorStoreHandle> stores = new ArrayList<>();
        List<VectorStoreStartupFailure> failures = new ArrayList<>();
        Set<VectorConnectionId> usedConnections = new LinkedHashSet<>();

        try {
            for (VectorStoreDefinition store : storeDefinitions) {
                StartupAttempt attempt = createStore(
                        store, connections, connectionRegistry, embeddingModelResolver, observationHook);
                if (attempt.handle() != null) {
                    stores.add(attempt.handle());
                    usedConnections.add(store.connectionId());
                } else if (store.required()) {
                    throw new VectorTopologyInitializationException(attempt.failure());
                } else {
                    failures.add(attempt.failure());
                }
            }
            closeUnusedConnections(connectionRegistry, usedConnections);
            return new VectorTopologyRuntime(
                    new DefaultVectorServiceRegistry(stores),
                    connectionRegistry,
                    failures);
        } catch (RuntimeException startupFailure) {
            try {
                connectionRegistry.close();
            } catch (VectorConnectionCloseException closeFailure) {
                startupFailure.addSuppressed(closeFailure);
            }
            throw startupFailure;
        }
    }

    private static StartupAttempt createStore(
            VectorStoreDefinition store,
            Map<VectorConnectionId, VectorConnectionDefinition> definitions,
            DefaultVectorConnectionRegistry connectionRegistry,
            NamedEmbeddingModelResolver embeddingModelResolver,
            VectorStoreObservationHook observationHook) {
        Objects.requireNonNull(store, "storeDefinitions must not contain null");
        VectorConnectionDefinition connectionDefinition = definitions.get(store.connectionId());
        if (connectionDefinition == null) {
            return StartupAttempt.failed(failure(store, VectorStoreStartupPhase.CONNECTION,
                    IllegalArgumentException.class));
        }

        VectorConnectionHandle connection;
        try {
            connection = connectionRegistry.open(connectionDefinition);
        } catch (RuntimeException exception) {
            return StartupAttempt.failed(failure(store, VectorStoreStartupPhase.CONNECTION, exception.getClass()));
        }

        VectorEmbeddingModelBinding embeddingModel;
        try {
            embeddingModel = Objects.requireNonNull(
                    embeddingModelResolver.resolve(store.embeddingModelRef()),
                    "embedding model resolver returned null")
                    .withContract(store.embeddingNormalization(), store.embeddingModalities());
            validateEmbeddingDimensions(store, embeddingModel);
        } catch (RuntimeException exception) {
            return StartupAttempt.failed(failure(store, VectorStoreStartupPhase.EMBEDDING_MODEL, exception.getClass()));
        }

        VectorStoreHandle handle;
        VectorStoreCapabilities expectedCapabilities;
        try {
            VectorProviderFactory factory = connectionRegistry.requireFactory(connectionDefinition.provider());
            expectedCapabilities = Objects.requireNonNull(
                    factory.capabilities(connectionDefinition, store),
                    "provider factory returned null capabilities");
            handle = Objects.requireNonNull(
                    factory.createStore(connection, store, embeddingModel),
                    "provider factory returned null store");
            if (observationHook != null) {
                handle = handle.observed(observationHook);
            }
        } catch (RuntimeException exception) {
            return StartupAttempt.failed(failure(store, VectorStoreStartupPhase.STORE_CREATION, exception.getClass()));
        }

        if (!store.id().equals(handle.id()) || handle.provider() != connectionDefinition.provider()
                || !store.connectionId().equals(handle.definition().connectionId())
                || !embeddingModel.fingerprint().equals(handle.embeddingModelFingerprint())
                || !expectedCapabilities.ids().equals(handle.storeCapabilities().ids())) {
            return StartupAttempt.failed(failure(store, VectorStoreStartupPhase.STORE_VALIDATION,
                    IllegalStateException.class));
        }
        try {
            handle.storeCapabilities().requireAll(store.id(), store.requiredCapabilities());
        } catch (RuntimeException exception) {
            return StartupAttempt.failed(failure(store, VectorStoreStartupPhase.STORE_VALIDATION, exception.getClass()));
        }
        return StartupAttempt.succeeded(handle);
    }

    private static void validateEmbeddingDimensions(
            VectorStoreDefinition store,
            VectorEmbeddingModelBinding binding) {
        if (binding.dimensions() == 0 || store.indexes().isEmpty()) {
            return;
        }
        boolean mismatch = store.indexes().values().stream()
                .anyMatch(index -> index.dimension() != binding.dimensions());
        if (mismatch) {
            throw new IllegalArgumentException("embedding model dimension does not match store indexes");
        }
    }

    private static Map<VectorConnectionId, VectorConnectionDefinition> indexConnections(
            Collection<VectorConnectionDefinition> definitions) {
        Map<VectorConnectionId, VectorConnectionDefinition> indexed = new LinkedHashMap<>();
        for (VectorConnectionDefinition definition : definitions) {
            Objects.requireNonNull(definition, "connectionDefinitions must not contain null");
            if (indexed.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate vector connection id: " + definition.id());
            }
        }
        return indexed;
    }

    private static void closeUnusedConnections(
            DefaultVectorConnectionRegistry registry,
            Set<VectorConnectionId> usedConnections) {
        for (VectorConnectionId connectionId : registry.openedConnectionIds()) {
            if (!usedConnections.contains(connectionId)) {
                registry.closeConnection(connectionId);
            }
        }
    }

    private static VectorStoreStartupFailure failure(
            VectorStoreDefinition store,
            VectorStoreStartupPhase phase,
            Class<?> failureType) {
        return new VectorStoreStartupFailure(store.id(), phase, failureType.getName());
    }

    private record StartupAttempt(VectorStoreHandle handle, VectorStoreStartupFailure failure) {

        static StartupAttempt succeeded(VectorStoreHandle handle) {
            return new StartupAttempt(handle, null);
        }

        static StartupAttempt failed(VectorStoreStartupFailure failure) {
            return new StartupAttempt(null, failure);
        }
    }
}
