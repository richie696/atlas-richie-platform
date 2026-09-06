/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreCapabilityIsolationTest {

    @Test
    void shouldKeepFilterAndAclCapabilitiesBoundToTheirOwnStore() {
        VectorStoreHandle milvus = handle(
                "milvus-store", VectorProvider.MILVUS, filter -> "milvus:" + ((VectorFilter.Eq) filter).value());
        VectorStoreHandle redis = handle(
                "redis-store", VectorProvider.REDIS, filter -> "redis:" + ((VectorFilter.Eq) filter).value());
        VectorServiceRegistry registry = new DefaultVectorServiceRegistry(List.of(milvus, redis));
        VectorFilter acl = VectorFilter.eq("tenantId", "synthetic-tenant-a");

        VectorStoreHandle resolvedMilvus = registry.require(VectorStoreId.of("milvus-store"));
        VectorStoreHandle resolvedRedis = registry.require(VectorStoreId.of("redis-store"));

        assertThat(resolvedMilvus.requireCapability(VectorFilterCompiler.class).compile(acl))
                .isEqualTo("milvus:synthetic-tenant-a");
        assertThat(resolvedRedis.requireCapability(VectorFilterCompiler.class).compile(acl))
                .isEqualTo("redis:synthetic-tenant-a");
        assertThat(resolvedMilvus.requireCapability(VectorFilterCompiler.class))
                .isNotSameAs(resolvedRedis.requireCapability(VectorFilterCompiler.class));
        assertThat(resolvedMilvus.storeCapabilities().supports(VectorCapability.ACL_FILTER)).isTrue();
        assertThat(resolvedRedis.storeCapabilities().supports(VectorCapability.ACL_FILTER)).isTrue();
    }

    private static VectorStoreHandle handle(
            String id, VectorProvider provider, VectorFilterCompiler compiler) {
        VectorStoreDefinition definition = new VectorStoreDefinition(
                VectorStoreId.of(id),
                VectorConnectionId.of(id + "-connection"),
                "syntheticEmbeddingModel",
                "synthetic-index",
                true,
                Set.of());
        return VectorStoreHandle.builder(definition, provider, proxy(VectorService.class))
                .storeCapabilities(VectorStoreCapabilities.of(
                        VectorCapability.NATIVE_FILTER,
                        VectorCapability.ACL_FILTER))
                .capability(VectorFilterCompiler.class, compiler)
                .build();
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "TestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                }));
    }
}
