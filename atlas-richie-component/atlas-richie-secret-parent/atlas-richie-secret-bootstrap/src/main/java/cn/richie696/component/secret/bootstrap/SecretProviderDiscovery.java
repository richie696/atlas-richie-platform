/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretProviderType;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Map;

/**
 * 启动期 Provider 的发现和无歧义选择器。
 */
public class SecretProviderDiscovery {

    public List<SecretBootstrapProviderFactory> discover(
            ClassLoader classLoader,
            BootstrapSecretProperties properties) {
        List<ServiceLoader.Provider<SecretBootstrapProviderFactory>> providers = ServiceLoader
                .load(SecretBootstrapProviderFactory.class, classLoader).stream().toList();
        if (configuredType(properties) == null && providers.size() > 1) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-002",
                    "Multiple Secret Providers were found; active-provider is required");
        }
        int providerCount = providers.size();
        providers = providers.stream()
                .filter(provider -> matchesConfiguredType(provider, properties, providerCount))
                .toList();
        return providers.stream()
                .map(ServiceLoader.Provider::get)
                .filter(factory -> factory.supports(properties))
                .toList();
    }

    private boolean matchesConfiguredType(
            ServiceLoader.Provider<SecretBootstrapProviderFactory> provider,
            BootstrapSecretProperties properties,
            int providerCount) {
        String configuredType = configuredType(properties);
        if (configuredType == null) {
            return true;
        }
        SecretProviderType descriptor = provider.type().getAnnotation(SecretProviderType.class);
        return descriptor != null
                ? descriptor.value().equalsIgnoreCase(configuredType)
                // Preserve compatibility for a single external SPI implementation while
                // preventing unannotated providers from being eagerly instantiated among peers.
                : providerCount == 1;
    }

    private String configuredType(BootstrapSecretProperties properties) {
        if (properties == null) {
            return null;
        }
        String activeProvider = properties.getActiveProvider();
        if (activeProvider == null || activeProvider.isBlank()) {
            return null;
        }
        Map<String, BootstrapSecretProperties.Provider> providers = properties.getProviders();
        if (providers.containsKey(activeProvider)
                && providers.get(activeProvider).getType() != null
                && !providers.get(activeProvider).getType().isBlank()) {
            return providers.get(activeProvider).getType();
        }
        return activeProvider;
    }

    public SecretBootstrapProviderFactory select(
            List<SecretBootstrapProviderFactory> factories,
            BootstrapSecretProperties properties) {
        if (factories.isEmpty()) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-001",
                    "Secret is enabled but no bootstrap Provider was found");
        }
        if (factories.size() == 1) {
            SecretBootstrapProviderFactory factory = factories.getFirst();
            String activeProvider = properties.getActiveProvider();
            if (activeProvider == null || activeProvider.isBlank()) {
                return factory;
            }
            String providerType = properties.getProviders().containsKey(activeProvider)
                    ? properties.getProviders().get(activeProvider).getType()
                    : activeProvider;
            if (providerType != null && factory.providerType().equalsIgnoreCase(providerType)) {
                return factory;
            }
            throw new SecretBootstrapException(
                    "SEC-BOOT-002",
                    "active-provider does not match the installed Secret Provider");
        }
        String activeProvider = properties.getActiveProvider();
        if (activeProvider == null || activeProvider.isBlank()) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-002",
                    "Multiple Secret Providers were found; active-provider is required");
        }
        String providerType = properties.getProviders().containsKey(activeProvider)
                ? properties.getProviders().get(activeProvider).getType()
                : activeProvider;
        List<SecretBootstrapProviderFactory> matches = factories.stream()
                .filter(factory -> factory.providerType().equalsIgnoreCase(providerType))
                .toList();
        if (matches.size() != 1) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-002",
                    "active-provider does not resolve to exactly one Secret Provider");
        }
        return matches.getFirst();
    }
}
