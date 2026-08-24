/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将单 Provider 或命名 Provider 配置解析为同一份 Vault 不可变启动视图。
 */
final class VaultSecretConfigurationResolver {

    ResolvedVaultConfiguration resolve(
            Environment environment,
            BootstrapSecretProperties bootstrapProperties) {
        String providerId = "vault";
        String prefix = VaultSecretProperties.PREFIX;
        String activeProvider = bootstrapProperties.getActiveProvider();
        if (activeProvider != null && !activeProvider.isBlank()
                && bootstrapProperties.getProviders().containsKey(activeProvider)) {
            providerId = activeProvider;
            prefix = BootstrapSecretProperties.PREFIX + ".providers." + activeProvider;
        }
        VaultSecretProperties properties = Binder.get(environment)
                .bind(prefix, VaultSecretProperties.class)
                .orElseGet(VaultSecretProperties::new);
        validate(properties);
        return new ResolvedVaultConfiguration(providerId, properties, configurationHash(providerId, properties));
    }

    private void validate(VaultSecretProperties properties) {
        URI endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.getHost() == null) {
            invalid("Vault endpoint is required");
        }
        if (endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            invalid("Vault endpoint must not contain user-info, query, or fragment");
        }
        String scheme = endpoint.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !("http".equalsIgnoreCase(scheme) && isLoopback(endpoint.getHost()))) {
            invalid("Vault endpoint must use HTTPS; HTTP is allowed only for loopback development");
        }
        safePath(properties.getKv().getMount(), "Vault KV mount");
        safePath(properties.getKv().getRuntimePrefix(), "Vault runtime prefix");
        safePath(properties.getTransit().getMount(), "Vault Transit mount");
        for (Map.Entry<String, String> binding : properties.getTransit().getKeyBindings().entrySet()) {
            safeLogicalName(binding.getKey(), "logical key binding");
            safePath(binding.getValue(), "Vault Transit key binding");
        }
        for (Map.Entry<String, VaultSecretProperties.SecretMapping> binding : properties.getSecrets().entrySet()) {
            safeLogicalName(binding.getKey(), "logical Secret binding");
            if (binding.getValue() == null) {
                invalid("Vault Secret mapping must not be null");
            }
            safePath(binding.getValue().getPath(), "Vault Secret path binding");
            if (binding.getValue().getField() != null) {
                safeLogicalName(binding.getValue().getField(), "Vault Secret field binding");
            }
        }
        validateAuthentication(properties.getAuthentication());
    }

    private void validateAuthentication(VaultSecretProperties.Authentication authentication) {
        switch (authentication.getType()) {
            case KUBERNETES -> {
                required(authentication.getRole(), "Vault Kubernetes role");
                safePath(authentication.getKubernetesPath(), "Vault Kubernetes auth path");
                required(authentication.getServiceAccountTokenFile(), "Kubernetes service account token file");
            }
            case TOKEN_FILE -> required(authentication.getTokenFile(), "Vault token file");
            case TOKEN -> {
                char[] token = authentication.getToken();
                try {
                    if (token.length == 0) {
                        invalid("Vault token is required for TOKEN authentication");
                    }
                } finally {
                    java.util.Arrays.fill(token, '\0');
                }
            }
            case APPROLE -> {
                safePath(authentication.getAppRolePath(), "Vault AppRole auth path");
                char[] roleId = authentication.getRoleId();
                char[] secretId = authentication.getSecretId();
                try {
                    if (isBlank(authentication.getRoleIdFile()) && roleId.length == 0) {
                        invalid("Vault AppRole role-id or role-id-file is required");
                    }
                    if (isBlank(authentication.getSecretIdFile()) && secretId.length == 0) {
                        invalid("Vault AppRole secret-id or secret-id-file is required");
                    }
                } finally {
                    java.util.Arrays.fill(roleId, '\0');
                    java.util.Arrays.fill(secretId, '\0');
                }
            }
            case JWT -> {
                safePath(authentication.getJwtPath(), "Vault JWT auth path");
                required(authentication.getJwtRole(), "Vault JWT role");
                char[] jwt = authentication.getJwt();
                try {
                    if (isBlank(authentication.getJwtFile()) && jwt.length == 0) {
                        invalid("Vault JWT or jwt-file is required");
                    }
                } finally {
                    java.util.Arrays.fill(jwt, '\0');
                }
            }
            case AGENT -> {
                // Vault Agent performs authentication and forwards requests.
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String configurationHash(String providerId, VaultSecretProperties properties) {
        String canonical = providerId + "\n"
                + properties.getEndpoint() + "\n"
                + nullToEmpty(properties.getNamespace()) + "\n"
                + properties.getAuthentication().getType() + "\n"
                + nullToEmpty(properties.getAuthentication().getKubernetesPath()) + "\n"
                + nullToEmpty(properties.getAuthentication().getAppRolePath()) + "\n"
                + nullToEmpty(properties.getAuthentication().getRoleIdFile()) + "\n"
                + nullToEmpty(properties.getAuthentication().getSecretIdFile()) + "\n"
                + nullToEmpty(properties.getAuthentication().getJwtPath()) + "\n"
                + nullToEmpty(properties.getAuthentication().getJwtRole()) + "\n"
                + nullToEmpty(properties.getAuthentication().getJwtFile()) + "\n"
                + nullToEmpty(properties.getAuthentication().getServiceAccountTokenFile()) + "\n"
                + properties.getKv().getMount() + "\n"
                + properties.getTransit().getMount() + "\n"
                + canonicalMap(properties.getTransit().getKeyBindings()) + "\n"
                + canonicalMap(properties.getSecrets());
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private String canonicalMap(Map<?, ?> values) {
        List<String> entries = new ArrayList<>();
        values.forEach((key, value) -> {
            String rendered = value instanceof VaultSecretProperties.SecretMapping mapping
                    ? nullToEmpty(mapping.getPath()) + "#" + nullToEmpty(mapping.getField())
                    : String.valueOf(value);
            entries.add(String.valueOf(key) + "=" + rendered);
        });
        entries.sort(Comparator.naturalOrder());
        return String.join("\u001f", entries);
    }

    private boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host)
                || "::1".equals(host);
    }

    private void safeLogicalName(String value, String label) {
        required(value, label);
        if (value.contains("..") || value.contains("://") || value.startsWith("/")) {
            invalid(label + " is invalid");
        }
    }

    private void safePath(String value, String label) {
        required(value, label);
        if (value.contains("..") || value.contains("://") || value.startsWith("/") || value.endsWith("/")) {
            invalid(label + " is invalid");
        }
    }

    private void required(String value, String label) {
        if (value == null || value.isBlank()) {
            invalid(label + " is required");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void invalid(String message) {
        throw new SecretConfigurationException("SEC-BOOT-003", message);
    }

    record ResolvedVaultConfiguration(
            String providerId,
            VaultSecretProperties properties,
            String configurationHash) {
    }
}
