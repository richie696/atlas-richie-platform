/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Synchronized connection registry with per-ID reuse and deterministic reverse-order close. */
public final class DefaultVectorConnectionRegistry implements VectorConnectionRegistry {

    private final Map<VectorProvider, VectorProviderFactory> factories;
    private final Map<VectorConnectionId, VectorConnectionDefinition> definitions = new LinkedHashMap<>();
    private final Map<VectorConnectionId, VectorConnectionHandle> connections = new LinkedHashMap<>();
    private boolean closed;

    public DefaultVectorConnectionRegistry(Collection<VectorProviderFactory> providerFactories) {
        Objects.requireNonNull(providerFactories, "providerFactories must not be null");
        Map<VectorProvider, VectorProviderFactory> registered = new LinkedHashMap<>();
        for (VectorProviderFactory factory : providerFactories) {
            Objects.requireNonNull(factory, "providerFactories must not contain null");
            VectorProvider provider = Objects.requireNonNull(factory.provider(), "factory provider must not be null");
            if (registered.putIfAbsent(provider, factory) != null) {
                throw new IllegalArgumentException("duplicate vector provider factory: " + provider);
            }
        }
        this.factories = Collections.unmodifiableMap(registered);
    }

    @Override
    public synchronized VectorConnectionHandle open(VectorConnectionDefinition definition) {
        ensureOpen();
        Objects.requireNonNull(definition, "definition must not be null");
        VectorConnectionDefinition existingDefinition = definitions.get(definition.id());
        if (existingDefinition != null) {
            if (existingDefinition.provider() != definition.provider()
                    || !existingDefinition.settings().equals(definition.settings())) {
                throw new IllegalArgumentException("conflicting vector connection definition: " + definition.id());
            }
            return connections.get(definition.id());
        }

        VectorProviderFactory factory = requireFactory(definition.provider());
        VectorConnectionHandle connection = Objects.requireNonNull(
                factory.openConnection(definition),
                "provider factory returned null connection: " + definition.id());
        if (!definition.id().equals(connection.id()) || definition.provider() != connection.provider()) {
            closeInvalidConnection(connection);
            throw new IllegalStateException("provider factory returned mismatched connection: " + definition.id());
        }
        definitions.put(definition.id(), definition);
        connections.put(definition.id(), connection);
        return connection;
    }

    @Override
    public synchronized Optional<VectorConnectionHandle> find(VectorConnectionId connectionId) {
        Objects.requireNonNull(connectionId, "connectionId must not be null");
        return Optional.ofNullable(connections.get(connectionId));
    }

    @Override
    public synchronized VectorConnectionHandle require(VectorConnectionId connectionId) {
        return find(connectionId).orElseThrow(() ->
                new IllegalArgumentException("vector connection is not open: " + connectionId));
    }

    @Override
    public VectorProviderFactory requireFactory(VectorProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        VectorProviderFactory factory = factories.get(provider);
        if (factory == null) {
            throw new IllegalArgumentException("vector provider factory is not available: " + provider);
        }
        return factory;
    }

    @Override
    public synchronized List<VectorConnectionId> openedConnectionIds() {
        return List.copyOf(connections.keySet());
    }

    @Override
    public synchronized void closeConnection(VectorConnectionId connectionId) {
        Objects.requireNonNull(connectionId, "connectionId must not be null");
        VectorConnectionHandle connection = connections.remove(connectionId);
        definitions.remove(connectionId);
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (RuntimeException exception) {
            throw new VectorConnectionCloseException(List.of(connectionId));
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Map.Entry<VectorConnectionId, VectorConnectionHandle>> opened = new ArrayList<>(connections.entrySet());
        connections.clear();
        definitions.clear();
        Collections.reverse(opened);

        List<VectorConnectionId> failed = new ArrayList<>();
        for (Map.Entry<VectorConnectionId, VectorConnectionHandle> entry : opened) {
            try {
                entry.getValue().close();
            } catch (RuntimeException exception) {
                failed.add(entry.getKey());
            }
        }
        if (!failed.isEmpty()) {
            throw new VectorConnectionCloseException(failed);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("vector connection registry is closed");
        }
    }

    private static void closeInvalidConnection(VectorConnectionHandle connection) {
        try {
            connection.close();
        } catch (RuntimeException ignored) {
            // The mismatch is the primary deterministic error; provider details remain redacted.
        }
    }
}
