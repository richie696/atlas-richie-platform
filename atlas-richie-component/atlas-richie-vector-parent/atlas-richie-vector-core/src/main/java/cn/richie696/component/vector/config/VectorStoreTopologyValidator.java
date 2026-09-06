/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorProviderFactory;
import cn.richie696.component.vector.topology.VectorStoreCapabilities;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorTopologyDefinitions;
import cn.richie696.component.vector.query.VectorQueryDefaults;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Startup validator for provider-neutral named topology rules. */
public final class VectorStoreTopologyValidator {

    private static final String PREFIX = "platform.component.vector.";
    private static final Set<String> STORE_FIELDS = Set.of(
            "connection-ref", "embedding-model-ref", "embedding-normalization", "embedding-modalities",
            "default-index", "required", "required-capabilities");
    private static final Set<String> QUERY_DEFAULT_FIELDS = Set.of(
            "top-k", "min-score", "candidate-limit", "timeout", "consistency", "return-fields");
    private static final Set<String> INDEX_FIELDS = Set.of(
            "name", "dimension", "metric", "index-type", "replicas", "shards");
    private static final Set<String> LEGACY_PROVIDER_PREFIXES = providerPrefixes();

    private final VectorProperties properties;
    private final ConfigurableEnvironment environment;
    private final Map<VectorProvider, VectorProviderFactory> factories;

    public VectorStoreTopologyValidator(
            VectorProperties properties,
            ConfigurableEnvironment environment,
            Collection<VectorProviderFactory> providerFactories) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.factories = indexFactories(providerFactories);
    }

    public VectorTopologyDefinitions validate() {
        properties.validateNamedTopology();
        if (!properties.hasNamedTopology()) {
            return new VectorTopologyDefinitions(java.util.List.of(), java.util.List.of());
        }

        Set<String> propertyNames = propertyNames(environment);
        rejectLegacyConflict(propertyNames);
        rejectUnknownNamedProperties(propertyNames);

        VectorTopologyDefinitions definitions = VectorTopologyDefinitions.from(properties);
        Map<cn.richie696.component.vector.topology.VectorConnectionId, VectorConnectionDefinition> connections =
                new LinkedHashMap<>();
        for (VectorConnectionDefinition connection : definitions.connections()) {
            VectorProviderFactory factory = factories.get(connection.provider());
            if (factory == null) {
                throw new IllegalArgumentException("vector provider factory is not available: " + connection.provider());
            }
            validateConnection(factory, connection);
            connections.put(connection.id(), connection);
        }
        Map<String, VectorStoreDefinition> physicalResourceOwners = new LinkedHashMap<>();
        for (VectorStoreDefinition store : definitions.stores()) {
            VectorConnectionDefinition connection = connections.get(store.connectionId());
            if (connection == null) {
                throw new IllegalArgumentException("vector store references unknown connection: " + store.id());
            }
            validateDefaultIndex(store);
            VectorProviderFactory factory = factories.get(connection.provider());
            validateStore(factory, connection, store);
            validatePhysicalResourceIsolation(
                    factory, connection, store, physicalResourceOwners);
            VectorStoreCapabilities capabilities = capabilities(factory, connection, store);
            capabilities.requireAll(store.id(), store.requiredCapabilities());
            validateQueryDefaults(store, capabilities);
        }
        return definitions;
    }

    private static void validatePhysicalResourceIsolation(
            VectorProviderFactory factory,
            VectorConnectionDefinition connection,
            VectorStoreDefinition store,
            Map<String, VectorStoreDefinition> owners) {
        Set<String> identities;
        try {
            identities = Objects.requireNonNull(
                    factory.physicalResourceIdentities(connection, store),
                    "provider factory returned null physical resource identities");
        } catch (RuntimeException exception) {
            throw sanitized("vector physical resource discovery failed", store.id().value(), exception);
        }
        for (String identity : identities) {
            if (identity == null || identity.isBlank()) {
                throw new IllegalArgumentException(
                        "vector physical resource discovery failed: " + store.id()
                                + " (type=" + IllegalArgumentException.class.getName() + ")");
            }
            String scopedIdentity = connection.id().value() + "\u0000" + identity;
            VectorStoreDefinition previous = owners.putIfAbsent(scopedIdentity, store);
            if (previous != null && !previous.id().equals(store.id())) {
                throw new IllegalArgumentException(
                        "vector stores resolve to the same physical resource on connection "
                                + connection.id() + ": " + previous.id() + ", " + store.id());
            }
        }
    }

    private static void validateQueryDefaults(
            VectorStoreDefinition store, VectorStoreCapabilities capabilities) {
        VectorQueryDefaults defaults = store.queryDefaults();
        if (defaults == null || (defaults.topK() == null && defaults.minScore() == null
                && defaults.candidateLimit() == null && defaults.timeout() == null
                && defaults.consistency() == null && defaults.returnFields().isEmpty())) {
            return;
        }
        boolean typedEntry = capabilities.descriptor(VectorCapability.QUERY_TUNING)
                .map(descriptor -> VectorAdvancedSearchOperations.class.getName()
                        .equals(descriptor.constraints().get("api")))
                .orElse(false);
        if (!typedEntry) {
            throw new IllegalArgumentException(
                    "vector store query-defaults require the typed advanced query capability: " + store.id());
        }
    }

    private static VectorStoreCapabilities capabilities(
            VectorProviderFactory factory,
            VectorConnectionDefinition connection,
            VectorStoreDefinition store) {
        try {
            return Objects.requireNonNull(
                    factory.capabilities(connection, store),
                    "provider factory returned null capabilities");
        } catch (RuntimeException exception) {
            throw sanitized("vector capability discovery failed", store.id().value(), exception);
        }
    }

    private static void validateConnection(VectorProviderFactory factory, VectorConnectionDefinition connection) {
        try {
            factory.validateConnection(connection);
        } catch (RuntimeException exception) {
            throw sanitized("vector connection validation failed", connection.id().value(), exception);
        }
    }

    private static void validateStore(
            VectorProviderFactory factory,
            VectorConnectionDefinition connection,
            VectorStoreDefinition store) {
        try {
            factory.validateStore(connection, store);
        } catch (RuntimeException exception) {
            throw sanitized("vector store validation failed", store.id().value(), exception);
        }
    }

    private static IllegalArgumentException sanitized(String prefix, String id, RuntimeException exception) {
        return new IllegalArgumentException(prefix + ": " + id + " (type=" + exception.getClass().getName() + ")");
    }

    private static void validateDefaultIndex(VectorStoreDefinition store) {
        if (store.indexes().isEmpty()) {
            return;
        }
        boolean declared = store.indexes().containsKey(store.defaultIndex())
                || store.indexes().values().stream().anyMatch(index -> index.name().equals(store.defaultIndex()));
        if (!declared) {
            throw new IllegalArgumentException("vector store default index is not declared: " + store.id());
        }
    }

    private static Map<VectorProvider, VectorProviderFactory> indexFactories(
            Collection<VectorProviderFactory> providerFactories) {
        Objects.requireNonNull(providerFactories, "providerFactories must not be null");
        Map<VectorProvider, VectorProviderFactory> indexed = new LinkedHashMap<>();
        for (VectorProviderFactory factory : providerFactories) {
            Objects.requireNonNull(factory, "providerFactories must not contain null");
            if (indexed.putIfAbsent(factory.provider(), factory) != null) {
                throw new IllegalArgumentException("duplicate vector provider factory: " + factory.provider());
            }
        }
        return indexed;
    }

    private static void rejectLegacyConflict(Set<String> names) {
        for (String name : names) {
            if (name.equals(PREFIX + "provider")
                    || name.equals(PREFIX + "default-index")
                    || name.startsWith(PREFIX + "indexes.")) {
                throw new IllegalArgumentException("named vector topology cannot be combined with legacy property: " + name);
            }
            for (String providerPrefix : LEGACY_PROVIDER_PREFIXES) {
                if (name.startsWith(PREFIX + providerPrefix + ".")) {
                    throw new IllegalArgumentException(
                            "named vector topology cannot be combined with legacy provider settings: "
                                    + PREFIX + providerPrefix);
                }
            }
        }
    }

    private static void rejectUnknownNamedProperties(Set<String> names) {
        for (String name : names) {
            if (name.startsWith(PREFIX + "stores.")) {
                validateStoreProperty(name.substring((PREFIX + "stores.").length()), name);
            } else if (name.startsWith(PREFIX + "connections.")) {
                validateConnectionProperty(name.substring((PREFIX + "connections.").length()), name);
            }
        }
    }

    private static void validateStoreProperty(String relative, String fullName) {
        String[] segments = relative.split("\\.");
        if (segments.length < 2) {
            throw unknown(fullName);
        }
        String field = segments[1];
        if (field.equals("query-defaults") && segments.length >= 3) {
            if (QUERY_DEFAULT_FIELDS.contains(segments[2])
                    && (segments.length == 3
                    || (segments[2].equals("return-fields") && segments.length <= 4))) {
                return;
            }
            throw unknown(fullName);
        }
        if (STORE_FIELDS.contains(field)) {
            if ((field.equals("required-capabilities") || field.equals("embedding-modalities"))
                    && segments.length <= 3) {
                return;
            }
            if (!field.equals("required-capabilities") && !field.equals("embedding-modalities")
                    && segments.length == 2) {
                return;
            }
            throw unknown(fullName);
        }
        if (!field.equals("indexes") || segments.length < 4) {
            throw unknown(fullName);
        }
        String indexField = segments[3];
        if (INDEX_FIELDS.contains(indexField) && segments.length == 4) {
            return;
        }
        if ((indexField.equals("additional-fields") || indexField.equals("index-params"))
                && segments.length >= 5) {
            return;
        }
        throw unknown(fullName);
    }

    private static void validateConnectionProperty(String relative, String fullName) {
        String[] segments = relative.split("\\.");
        if (segments.length == 2 && segments[1].equals("provider")) {
            return;
        }
        if (segments.length >= 3 && segments[1].equals("settings")) {
            return;
        }
        throw unknown(fullName);
    }

    private static IllegalArgumentException unknown(String propertyName) {
        return new IllegalArgumentException("unknown named vector property: " + propertyName);
    }

    private static Set<String> propertyNames(ConfigurableEnvironment environment) {
        Set<String> names = new LinkedHashSet<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                String canonical = canonicalize(name);
                if (canonical.startsWith(PREFIX)) {
                    names.add(canonical);
                }
            }
        }
        return names;
    }

    private static String canonicalize(String name) {
        return name.trim()
                .replace('[', '.')
                .replace("]", "")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\.+", ".");
    }

    private static Set<String> providerPrefixes() {
        Set<String> prefixes = new LinkedHashSet<>();
        for (VectorProvider provider : VectorProvider.values()) {
            prefixes.add(provider.name().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(prefixes);
    }
}
