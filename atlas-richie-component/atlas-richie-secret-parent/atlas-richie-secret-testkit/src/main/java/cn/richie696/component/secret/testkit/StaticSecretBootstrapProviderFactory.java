/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.testkit;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动期测试使用的静态 Provider，不执行网络或线程操作。
 */
public final class StaticSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private final String providerType;
    private final Map<String, Object> values;

    public StaticSecretBootstrapProviderFactory(String providerType, Map<String, Object> values) {
        this.providerType = providerType;
        this.values = deepCopyMap(values);
    }

    @Override
    public String providerType() {
        return providerType;
    }

    @Override
    public Set<SecretCapability> capabilities() {
        return Set.of(SecretCapability.SECRET_READ);
    }

    @Override
    public SecretBootstrapClient create(
            BootstrapSecretProperties properties,
            SecretBootstrapContext context) {
        return request -> new SecretBootstrapResult(
                providerType,
                "test-v1",
                String.join(",", request.logicalPaths()),
                Instant.EPOCH,
                deepCopyMap(values),
                "test-request");
    }

    private static Map<String, Object> deepCopyMap(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key, deepCopy(value)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object deepCopy(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (value instanceof char[] chars) {
            return chars.clone();
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(key, deepCopy(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(nested -> copy.add(deepCopy(nested)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new java.util.LinkedHashSet<>();
            set.forEach(nested -> copy.add(deepCopy(nested)));
            return Collections.unmodifiableSet(copy);
        }
        return value;
    }
}
