/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.exceptions.VectorStoreAccessDeniedException;
import cn.richie696.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizedVectorStoreResolverTest {

    @Test
    void shouldResolveOnlyTheLogicalStoreAllowedByApplicationPolicy() {
        VectorStoreHandle knowledge = handle("knowledge");
        VectorStoreHandle prompt = handle("prompt");
        AuthorizedVectorStoreResolver resolver = new AuthorizedVectorStoreResolver(
                new DefaultVectorServiceRegistry(List.of(knowledge, prompt)),
                (request, store) -> request.attributes().getOrDefault("allowed-store", "").equals(store.id().value()));

        VectorStoreAccessRequest allowed = new VectorStoreAccessRequest(
                "application", "search", VectorStoreId.of("knowledge"), Map.of("allowed-store", "knowledge"));
        VectorStoreAccessRequest denied = new VectorStoreAccessRequest(
                "application", "search", VectorStoreId.of("prompt"), Map.of("allowed-store", "knowledge"));

        assertThat(resolver.require(allowed)).isSameAs(knowledge);
        assertThatThrownBy(() -> resolver.require(denied))
                .isInstanceOf(VectorStoreAccessDeniedException.class)
                .hasMessage("vector store access denied");
    }

    @Test
    void shouldKeepSimpleProjectsOnExplicitInProcessDefault() {
        VectorStoreHandle handle = handle("default");
        AuthorizedVectorStoreResolver resolver = new AuthorizedVectorStoreResolver(
                new DefaultVectorServiceRegistry(List.of(handle)), VectorStoreAccessPolicy.internalOnly());

        assertThat(resolver.require(VectorStoreAccessRequest.internal("search", VectorStoreId.of("default"))))
                .isSameAs(handle);
        assertThatThrownBy(() -> resolver.require(new VectorStoreAccessRequest(
                "external", "search", VectorStoreId.of("default"), Map.of())))
                .isInstanceOf(VectorStoreAccessDeniedException.class);
    }

    private static VectorStoreHandle handle(String storeId) {
        VectorStoreDefinition definition = new VectorStoreDefinition(
                VectorStoreId.of(storeId),
                VectorConnectionId.of("test-connection"),
                "testEmbeddingModel",
                "documents",
                true,
                Set.of());
        VectorService service = (VectorService) Proxy.newProxyInstance(
                VectorService.class.getClassLoader(),
                new Class<?>[]{VectorService.class},
                (proxy, method, args) -> null);
        return VectorStoreHandle.builder(definition, VectorProvider.MILVUS, service).build();
    }
}
