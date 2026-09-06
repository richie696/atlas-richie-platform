/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.exceptions.VectorStoreNotExistException;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.service.VectorService;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.query.VectorQueryErrorCode;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorQueryValidationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorTopologyContractsTest {

    @Test
    void shouldValidateStableIdentifiers() {
        assertThat(VectorStoreId.of("primary-search").value()).isEqualTo("primary-search");
        assertThat(VectorConnectionId.of("pg_shared").value()).isEqualTo("pg_shared");

        assertThatThrownBy(() -> VectorStoreId.of("Primary Search"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VectorConnectionId.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepDefinitionsImmutableAndConnectionStringRedacted() {
        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        settings.put("password", "secret-value");
        VectorConnectionDefinition connection = new VectorConnectionDefinition(
                VectorConnectionId.of("primary"), VectorProvider.POSTGRESQL, settings);
        settings.put("host", "changed-after-construction");

        assertThat(connection.settings()).containsExactlyEntriesOf(Map.of("password", "secret-value"));
        assertThatThrownBy(() -> connection.settings().put("host", "forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(connection.toString()).doesNotContain("secret-value").contains("<redacted>");

        Set<String> requiredCapabilities = new java.util.LinkedHashSet<>(Set.of("ACL_FILTER"));
        VectorStoreDefinition store = definition("primary-search", requiredCapabilities);
        requiredCapabilities.add("HYBRID_SEARCH");

        assertThat(store.requiredCapabilities()).containsExactly("ACL_FILTER");
        assertThatThrownBy(() -> store.requiredCapabilities().add("QUERY_TUNING"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldExposeOptionalCapabilitiesWithoutChangingBaseService() {
        VectorService service = proxy(VectorService.class);
        VectorFilterCompiler compiler = proxy(VectorFilterCompiler.class);
        VectorStoreHandle handle = VectorStoreHandle
                .builder(definition("primary-search", Set.of()), VectorProvider.MILVUS, service)
                .capability(VectorFilterCompiler.class, compiler)
                .build();

        assertThat(handle.service()).isSameAs(service);
        assertThat(handle.capability(VectorService.class)).contains(service);
        assertThat(handle.capability(VectorFilterCompiler.class)).contains(compiler);
        assertThat(handle.descriptor().exposedCapabilityTypes())
                .containsExactly(VectorFilterCompiler.class.getName());
        assertThatThrownBy(() -> handle.requireCapability(Runnable.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("primary-search")
                .hasMessageContaining(Runnable.class.getName());
    }

    @Test
    void shouldRejectDuplicateCapabilityRegistration() {
        VectorService service = proxy(VectorService.class);
        VectorFilterCompiler compiler = proxy(VectorFilterCompiler.class);
        VectorStoreHandle.Builder builder = VectorStoreHandle
                .builder(definition("primary-search", Set.of()), VectorProvider.MILVUS, service)
                .capability(VectorFilterCompiler.class, compiler);

        assertThatThrownBy(() -> builder.capability(VectorFilterCompiler.class, compiler))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate vector capability");
    }

    @Test
    void advancedCapabilityFailureDoesNotChangeTheBaseServicePath() {
        VectorService service = proxy(VectorService.class);
        VectorAdvancedSearchOperations rejected = request -> {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.UNSUPPORTED_OPTION, "synthetic advanced rejection");
        };
        VectorStoreHandle handle = VectorStoreHandle
                .builder(definition("primary-search", Set.of()), VectorProvider.MILVUS, service)
                .capability(VectorAdvancedSearchOperations.class, rejected)
                .build();

        assertThatThrownBy(() -> handle.requireCapability(VectorAdvancedSearchOperations.class)
                .search(VectorQueryRequest.of("advanced query")))
                .isInstanceOf(VectorQueryValidationException.class);
        assertThat(handle.service()).isSameAs(service);
        assertThat(handle.service().searchByText("documents", "basic query", 1, null)).isNull();
    }

    @Test
    void shouldProvideImmutableExactLookupRegistry() {
        VectorStoreHandle primary = handle("primary-search");
        VectorStoreHandle secondary = handle("normalized-content");
        DefaultVectorServiceRegistry registry = new DefaultVectorServiceRegistry(List.of(primary, secondary));

        assertThat(registry.find(VectorStoreId.of("primary-search"))).contains(primary);
        assertThat(registry.require(VectorStoreId.of("normalized-content"))).isSameAs(secondary);
        assertThat(registry.describeStores()).extracting(VectorStoreDescriptor::id)
                .containsExactly(VectorStoreId.of("primary-search"), VectorStoreId.of("normalized-content"));
        assertThatThrownBy(() -> registry.describeStores().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.require(VectorStoreId.of("missing-store")))
                .isInstanceOf(VectorStoreNotExistException.class)
                .hasMessage("missing-store");
    }

    @Test
    void shouldRejectDuplicateStoreIds() {
        assertThatThrownBy(() -> new DefaultVectorServiceRegistry(List.of(
                handle("primary-search"),
                handle("primary-search"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate vector store id: primary-search");
    }

    @Test
    void shouldAllowConcurrentReadOnlyLookup() {
        VectorStoreHandle handle = handle("primary-search");
        DefaultVectorServiceRegistry registry = new DefaultVectorServiceRegistry(List.of(handle));
        List<VectorStoreHandle> results = java.util.Collections.synchronizedList(new ArrayList<>());

        IntStream.range(0, 1_000).parallel()
                .mapToObj(ignored -> registry.require(VectorStoreId.of("primary-search")))
                .forEach(results::add);

        assertThat(results).hasSize(1_000).allMatch(candidate -> candidate == handle);
    }

    private static VectorStoreHandle handle(String storeId) {
        return VectorStoreHandle.builder(
                        definition(storeId, Set.of()),
                        VectorProvider.MILVUS,
                        proxy(VectorService.class))
                .build();
    }

    private static <T> T proxy(Class<T> type) {
        Object instance = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "TestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        return type.cast(instance);
    }

    private static VectorStoreDefinition definition(String storeId, Set<String> requiredCapabilities) {
        return new VectorStoreDefinition(
                VectorStoreId.of(storeId),
                VectorConnectionId.of("primary"),
                "aiEmbeddingModel",
                "documents",
                true,
                requiredCapabilities);
    }
}
