/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动期已解析的 Provider 实例拓扑。
 *
 * <p>业务代码不接触该类型。Starter 只通过能力路由取得内部 Provider，
 * 从而保持公开 API 与具体 KMS、Vault 或 HSM 实现解耦。</p>
 */
public final class SecretProviderTopology implements AutoCloseable {
    public static final String PROPERTY_SOURCE = "property-source";
    public static final String SECRET_READ = "secret-read";
    public static final String ENVELOPE_CRYPTO = "envelope-crypto";
    public static final String SIGNING = "signing";

    private static final Set<String> SUPPORTED_ROUTES = Set.of(
            PROPERTY_SOURCE, SECRET_READ, ENVELOPE_CRYPTO, SIGNING);

    private final Map<String, SecretBootstrapProviderFactory> factories;
    private final Map<String, SecretBootstrapClient> clients;
    private final Map<String, String> routing;
    private final String defaultProviderId;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SecretProviderTopology(
            Map<String, SecretBootstrapProviderFactory> factories,
            Map<String, SecretBootstrapClient> clients,
            Map<String, String> routing,
            String defaultProviderId) {
        this.factories = Map.copyOf(new LinkedHashMap<>(factories));
        this.clients = Map.copyOf(new LinkedHashMap<>(clients));
        this.routing = Map.copyOf(new LinkedHashMap<>(routing));
        this.defaultProviderId = normalize(defaultProviderId);
        validate();
    }

    public Map<String, SecretBootstrapProviderFactory> factories() {
        return factories;
    }

    public Map<String, SecretBootstrapClient> clients() {
        return clients;
    }

    public Map<String, String> routing() {
        return routing;
    }

    public String defaultProviderId() {
        return defaultProviderId;
    }

    public String providerId(String route) {
        String providerId = resolvedProviderId(route);
        requireCapabilities(route, providerId);
        return providerId;
    }

    public boolean supportsRoute(String route) {
        if (!SUPPORTED_ROUTES.contains(route)) return false;
        try {
            String providerId = resolvedProviderId(route);
            return actualCapabilities(providerId).containsAll(requiredCapabilities(route));
        } catch (SecretBootstrapException ignored) {
            return false;
        }
    }

    private String resolvedProviderId(String route) {
        String explicit = normalize(routing.get(route));
        if (explicit != null) {
            return requireKnown(explicit, route);
        }
        if (defaultProviderId != null) {
            return requireKnown(defaultProviderId, route);
        }
        if (clients.size() == 1) {
            return clients.keySet().iterator().next();
        }
        throw new SecretBootstrapException(
                "SEC-BOOT-002",
                "Secret route '" + route + "' requires an explicit Provider");
    }

    public SecretBootstrapProviderFactory factory(String providerId) {
        SecretBootstrapProviderFactory factory = factories.get(providerId);
        if (factory == null) {
            throw unknown(providerId);
        }
        return factory;
    }

    public SecretBootstrapClient client(String providerId) {
        SecretBootstrapClient client = clients.get(providerId);
        if (client == null) {
            throw unknown(providerId);
        }
        return client;
    }

    public SecretBootstrapClient routedClient(String route) {
        return client(providerId(route));
    }

    private void validate() {
        if (factories.isEmpty() || clients.isEmpty() || !factories.keySet().equals(clients.keySet())) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-002", "Secret Provider topology is incomplete");
        }
        for (Map.Entry<String, String> route : routing.entrySet()) {
            if (!SUPPORTED_ROUTES.contains(route.getKey())) {
                throw new SecretBootstrapException(
                        "SEC-BOOT-002", "Unsupported Secret route: " + route.getKey());
            }
            String providerId = requireKnown(route.getValue(), route.getKey());
            requireCapabilities(route.getKey(), providerId);
        }
        if (defaultProviderId != null) {
            requireKnown(defaultProviderId, "active-provider");
        }
    }

    private void requireCapabilities(String route, String providerId) {
        Set<SecretCapability> required = requiredCapabilities(route);
        Set<SecretCapability> actual = actualCapabilities(providerId);
        if (!actual.containsAll(required)) {
            throw new SecretBootstrapException(
                    "SEC-CAP-001",
                    "Secret route '" + route + "' requires capabilities " + required
                            + " but Provider '" + providerId + "' exposes " + actual);
        }
    }

    private Set<SecretCapability> requiredCapabilities(String route) {
        return switch (route) {
            case PROPERTY_SOURCE, SECRET_READ -> Set.of(SecretCapability.SECRET_READ);
            case ENVELOPE_CRYPTO -> Set.of(SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
            case SIGNING -> Set.of(SecretCapability.SIGN, SecretCapability.VERIFY);
            default -> Set.of();
        };
    }

    private Set<SecretCapability> actualCapabilities(String providerId) {
        SecretBootstrapClient client = clients.get(providerId);
        if (client instanceof SecretProviderSession session) {
            return session.descriptor().capabilities();
        }
        return factories.get(providerId).capabilities();
    }

    private String requireKnown(String providerId, String route) {
        String normalized = normalize(providerId);
        if (normalized == null || !clients.containsKey(normalized)) {
            throw new SecretBootstrapException(
                    "SEC-BOOT-002",
                    "Secret route '" + route + "' references unknown Provider '" + providerId + "'");
        }
        return normalized;
    }

    private SecretBootstrapException unknown(String providerId) {
        return new SecretBootstrapException(
                "SEC-BOOT-002", "Unknown Secret Provider instance: " + providerId);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Set<SecretBootstrapClient> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(clients.values());
        RuntimeException first = null;
        for (SecretBootstrapClient client : unique) {
            try {
                client.close();
            } catch (RuntimeException failure) {
                if (first == null) {
                    first = failure;
                } else {
                    first.addSuppressed(failure);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
