/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.catalog;

import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 合并并验证后的全局 Binding Catalog。
 */
public final class SecretBindingCatalogSet {
    private final Map<String, SecretBinding> bindings;
    private final Map<String, SecretPropertyPattern> dynamicPatterns;

    SecretBindingCatalogSet(Map<String, SecretBinding> bindings) {
        this.bindings = Map.copyOf(new LinkedHashMap<>(bindings));
        Map<String, SecretPropertyPattern> patterns = new LinkedHashMap<>();
        this.bindings.values().stream()
                .filter(binding -> binding.exposure() == SecretExposure.PROPERTY_SOURCE)
                .forEach(binding -> {
                    SecretPropertyPattern pattern = SecretPropertyPattern.compile(binding.property());
                    if (pattern != null && pattern.dynamic()) {
                        patterns.put(binding.property(), pattern);
                    }
                });
        this.dynamicPatterns = Map.copyOf(patterns);
    }

    public List<SecretBinding> bindings() {
        return List.copyOf(bindings.values());
    }

    public Set<String> propertySourceProperties() {
        return bindings.values().stream()
                .filter(binding -> binding.exposure() == SecretExposure.PROPERTY_SOURCE)
                .map(SecretBinding::property)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 查找精确或受约束动态模式对应的 PropertySource Binding。 */
    public Optional<SecretBinding> propertySourceBinding(String property) {
        SecretBinding exact = bindings.get(property);
        if (exact != null && exact.exposure() == SecretExposure.PROPERTY_SOURCE) return Optional.of(exact);
        SecretBinding matched = null;
        for (SecretBinding binding : bindings.values()) {
            if (binding.exposure() != SecretExposure.PROPERTY_SOURCE) continue;
            SecretPropertyPattern pattern = dynamicPatterns.get(binding.property());
            if (pattern != null && pattern.matches(property)) {
                if (matched != null) {
                    throw new cn.richie696.component.secret.api.exception.SecretConfigurationException(
                            "SEC-BOOT-003", "Secret property matches multiple Binding patterns: " + property);
                }
                matched = binding;
            }
        }
        return Optional.ofNullable(matched);
    }

    public List<SecretBinding> requiredBindings(Environment environment) {
        return bindings.values().stream()
                .filter(binding -> isRequired(binding.requiredWhen(), environment))
                .toList();
    }

    private boolean isRequired(RequiredWhen requiredWhen, Environment environment) {
        if (requiredWhen == null || requiredWhen.property() == null || requiredWhen.in().isEmpty()) {
            return false;
        }
        String current = environment.getProperty(requiredWhen.property());
        return current != null && requiredWhen.in().contains(current);
    }
}
