/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretProviderType;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Map;
import java.util.Set;

/**
 * 启动期 Provider 的发现和无歧义选择器。
 */
public class SecretProviderDiscovery {

    public List<SecretBootstrapProviderFactory> discover(
            ClassLoader classLoader,
            BootstrapSecretProperties properties) {
        List<ServiceLoader.Provider<SecretBootstrapProviderFactory>> providers = ServiceLoader
                .load(SecretBootstrapProviderFactory.class, classLoader).stream().toList();
        Set<String> configuredTypes = configuredTypes(properties);
        int providerCount = providers.size();
        providers = providers.stream()
                .filter(provider -> matchesConfiguredTypes(
                        provider, configuredTypes, providerCount))
                .toList();
        return providers.stream()
                .map(ServiceLoader.Provider::get)
                .filter(factory -> factory.supports(properties))
                .toList();
    }

    private boolean matchesConfiguredTypes(
            ServiceLoader.Provider<SecretBootstrapProviderFactory> provider,
            Set<String> configuredTypes,
            int providerCount) {
        if (configuredTypes.isEmpty()) {
            return true;
        }
        SecretProviderType descriptor = provider.type().getAnnotation(SecretProviderType.class);
        return descriptor != null
                ? configuredTypes.stream().anyMatch(
                        configured -> descriptor.value().equalsIgnoreCase(configured))
                // Preserve compatibility for a single external SPI implementation while
                // preventing unannotated providers from being eagerly instantiated among peers.
                : providerCount == 1;
    }

    private Set<String> configuredTypes(BootstrapSecretProperties properties) {
        Set<String> result = new LinkedHashSet<>();
        if (properties == null) return result;
        properties.getProviders().values().stream()
                .map(BootstrapSecretProperties.Provider::getType)
                .filter(type -> type != null && !type.isBlank())
                .forEach(result::add);
        if (!result.isEmpty()) return result;
        String activeProvider = properties.getActiveProvider();
        if (activeProvider != null && !activeProvider.isBlank()) result.add(activeProvider);
        properties.getRouting().values().stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(result::add);
        return result;
    }

    /**
     * 将已发现的 Provider 类型解析为命名实例。配置了 providers 时，实例 ID
     * 与配置 Map key 一致；单 Provider 兼容模式使用 providerType 作为 ID。
     */
    public Map<String, SecretBootstrapProviderFactory> selectAll(
            List<SecretBootstrapProviderFactory> factories,
            BootstrapSecretProperties properties) {
        if (factories.isEmpty()) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-001", "Secret is enabled but no bootstrap Provider was found");
        }
        Map<String, SecretBootstrapProviderFactory> result = new LinkedHashMap<>();
        Map<String, BootstrapSecretProperties.Provider> configured = properties.getProviders();
        if (!configured.isEmpty()) {
            for (Map.Entry<String, BootstrapSecretProperties.Provider> entry : configured.entrySet()) {
                String providerId = entry.getKey();
                String providerType = entry.getValue() == null ? null : entry.getValue().getType();
                if (providerId == null || providerId.isBlank()
                        || providerType == null || providerType.isBlank()) {
                    throw new SecretBootstrapException(
                            "SEC-BOOT-002", "Named Secret Provider requires a non-blank id and type");
                }
                result.put(providerId, uniqueFactory(factories, providerType));
            }
            return Map.copyOf(result);
        }

        String activeProvider = properties.getActiveProvider();
        if (activeProvider != null && !activeProvider.isBlank()) {
            SecretBootstrapProviderFactory factory = uniqueFactory(factories, activeProvider);
            result.put(factory.providerType(), factory);
            return Map.copyOf(result);
        }

        if (factories.size() == 1) {
            SecretBootstrapProviderFactory factory = factories.getFirst();
            result.put(factory.providerType(), factory);
            return Map.copyOf(result);
        }

        if (!properties.getRouting().isEmpty()) {
            factories.forEach(factory -> result.put(factory.providerType(), factory));
            return Map.copyOf(result);
        }
        throw new SecretBootstrapException(
                "SEC-BOOT-002",
                "Multiple Secret Providers were found; active-provider or complete routing is required");
    }

    private SecretBootstrapProviderFactory uniqueFactory(
            List<SecretBootstrapProviderFactory> factories,
            String providerType) {
        List<SecretBootstrapProviderFactory> matches = factories.stream()
                .filter(factory -> factory.providerType().equalsIgnoreCase(providerType))
                .toList();
        if (matches.size() != 1) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-002",
                    "Secret Provider type '" + providerType + "' does not resolve to exactly one implementation");
        }
        return matches.getFirst();
    }

    public SecretBootstrapProviderFactory select(
            List<SecretBootstrapProviderFactory> factories,
            BootstrapSecretProperties properties) {
        Map<String, SecretBootstrapProviderFactory> selected = selectAll(factories, properties);
        String activeProvider = properties.getActiveProvider();
        if (activeProvider != null && selected.containsKey(activeProvider)) {
            return selected.get(activeProvider);
        }
        if (selected.size() == 1) {
            return selected.values().iterator().next();
        }
        String propertySource = properties.getRouting().get(SecretProviderTopology.PROPERTY_SOURCE);
        if (propertySource != null && selected.containsKey(propertySource)) {
            return selected.get(propertySource);
        }
        throw new SecretBootstrapException(
                "SEC-BOOT-002", "Property-source Secret Provider is not configured");
    }
}
