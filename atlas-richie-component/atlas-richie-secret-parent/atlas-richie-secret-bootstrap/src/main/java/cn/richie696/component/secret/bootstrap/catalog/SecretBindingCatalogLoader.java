/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.catalog;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.SecretPropertyPolicy;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从所有依赖 JAR 扫描、解析并合并 Binding Catalog。
 */
public class SecretBindingCatalogLoader {
    public static final String RESOURCE_PATH = "META-INF/atlas-richie/secret-bindings.json";
    private static final int MAX_CATALOG_BYTES = 1024 * 1024;
    private static final int MAX_BINDINGS = 2048;

    private final JsonMapper jsonMapper;

    public SecretBindingCatalogLoader() {
        this(JsonMapper.builder().build());
    }

    SecretBindingCatalogLoader(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    public SecretBindingCatalogSet load(ClassLoader classLoader) {
        Map<String, SecretBinding> merged = new LinkedHashMap<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(RESOURCE_PATH);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                merge(read(resource), merged, resource);
            }
            validateSharedLogicalNames(merged);
            return new SecretBindingCatalogSet(merged);
        } catch (SecretConfigurationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Unable to scan Secret Binding Catalog resources",
                    exception);
        }
    }

    private SecretBindingCatalog read(URL resource) {
        try (InputStream input = resource.openStream()) {
            byte[] content = input.readNBytes(MAX_CATALOG_BYTES + 1);
            if (content.length > MAX_CATALOG_BYTES) {
                throw new SecretConfigurationException(
                        "SEC-BOOT-003",
                        "Secret Binding Catalog exceeds size limit: " + resource);
            }
            return jsonMapper.readValue(content, SecretBindingCatalog.class);
        } catch (IOException exception) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Invalid Secret Binding Catalog: " + resource,
                    exception);
        }
    }

    private void merge(
            SecretBindingCatalog catalog,
            Map<String, SecretBinding> merged,
            URL resource) {
        if (catalog == null || !"1".equals(catalog.schemaVersion())) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Unsupported Secret Binding Catalog schema at " + resource);
        }
        if (catalog.component() == null || catalog.component().isBlank()) {
            throw new SecretConfigurationException("SEC-BOOT-003", "Catalog component must not be blank");
        }
        if (catalog.bindings().size() > MAX_BINDINGS) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Secret Binding Catalog declares too many bindings: " + catalog.component());
        }
        for (SecretBinding binding : catalog.bindings()) {
            validate(binding, catalog.component());
            SecretBinding existing = merged.putIfAbsent(binding.property(), binding);
            if (existing != null && !compatible(existing, binding)) {
                throw new SecretConfigurationException(
                        "SEC-BOOT-003",
                        "Conflicting Secret Binding for property " + binding.property());
            }
        }
    }

    private void validate(SecretBinding binding, String component) {
        if (binding == null
                || binding.property() == null
                || binding.logicalName() == null
                || binding.logicalName().isBlank()
                || binding.kind() == null
                || binding.exposure() == null
                || binding.refresh() == null
                || binding.owner() == null
                || binding.owner().isBlank()) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Invalid Secret Binding declared by " + component);
        }
        SecretPropertyPattern pattern = SecretPropertyPattern.compile(binding.property());
        if (pattern == null) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Invalid Secret Binding property pattern declared by " + component + ": " + binding.property());
        }
        if (SecretPropertyPolicy.isForbidden(binding.property())) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Secret Binding targets a forbidden control property: " + binding.property());
        }
        if (pattern.dynamic() && binding.requiredWhen() != null) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Dynamic Secret Binding cannot declare requiredWhen: " + binding.property());
        }
        if (binding.maxLength() != null && binding.maxLength() < 1) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Secret Binding maxLength must be positive for " + binding.property());
        }
    }

    private boolean compatible(SecretBinding left, SecretBinding right) {
        return left.equals(right);
    }

    private void validateSharedLogicalNames(Map<String, SecretBinding> merged) {
        Map<String, List<SecretBinding>> byLogicalName = merged.values().stream()
                .collect(Collectors.groupingBy(SecretBinding::logicalName));
        for (Map.Entry<String, List<SecretBinding>> entry : byLogicalName.entrySet()) {
            if (entry.getValue().size() > 1
                    && entry.getValue().stream().anyMatch(binding -> !Boolean.TRUE.equals(binding.shared()))) {
                throw new SecretConfigurationException(
                        "SEC-BOOT-003",
                        "Secret logicalName maps to multiple properties without shared=true: " + entry.getKey());
            }
        }
    }
}
