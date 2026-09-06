/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.exceptions.VectorStoreNotExistException;
import cn.richie696.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorConnectionLifecycleTest {

    @Test
    void shouldReuseOneConnectionForMultipleStores() {
        FakeProviderFactory factory = new FakeProviderFactory();
        VectorConnectionDefinition connection = connection("shared");

        VectorTopologyRuntime runtime = VectorTopologyBootstrap.start(
                List.of(connection),
                List.of(store("search-a", "shared", true), store("search-b", "shared", true)),
                List.of(factory),
                ignored -> embeddingBinding());

        assertThat(factory.openCount.get()).isEqualTo(1);
        assertThat(factory.storeConnections).hasSize(2).allMatch(handle -> handle == factory.opened.get("shared"));
        assertThat(runtime.serviceRegistry().describeStores()).hasSize(2);

        runtime.close();
        runtime.close();
        assertThat(factory.opened.get("shared").closeCount.get()).isEqualTo(1);
    }

    @Test
    void shouldIsolateDifferentConnectionsForTheSameProviderAndCloseInReverseOrder() {
        List<String> closeOrder = new ArrayList<>();
        FakeProviderFactory factory = new FakeProviderFactory(closeOrder);
        DefaultVectorConnectionRegistry registry = new DefaultVectorConnectionRegistry(List.of(factory));

        VectorConnectionHandle first = registry.open(connection("first"));
        VectorConnectionHandle second = registry.open(connection("second"));

        assertThat(first).isNotSameAs(second);
        assertThat(registry.require(VectorConnectionId.of("first"))).isSameAs(first);
        assertThat(registry.require(VectorConnectionId.of("second"))).isSameAs(second);

        registry.close();
        assertThat(closeOrder).containsExactly("second", "first");
    }

    @Test
    void shouldRejectDuplicateFactoriesAndConflictingConnectionDefinitions() {
        FakeProviderFactory firstFactory = new FakeProviderFactory();
        FakeProviderFactory secondFactory = new FakeProviderFactory();

        assertThatThrownBy(() -> new DefaultVectorConnectionRegistry(List.of(firstFactory, secondFactory)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate vector provider factory: MILVUS");

        DefaultVectorConnectionRegistry registry = new DefaultVectorConnectionRegistry(List.of(firstFactory));
        registry.open(connection("primary"));
        VectorConnectionDefinition conflicting = new VectorConnectionDefinition(
                VectorConnectionId.of("primary"), VectorProvider.MILVUS, Map.of("host", "different.invalid"));

        assertThatThrownBy(() -> registry.open(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("conflicting vector connection definition: primary");
        registry.close();
    }

    @Test
    void shouldRollbackAllConnectionsWhenRequiredStoreFails() {
        List<String> closeOrder = new ArrayList<>();
        FakeProviderFactory factory = new FakeProviderFactory(closeOrder);
        factory.failingStores.add("required-failure");

        assertThatThrownBy(() -> VectorTopologyBootstrap.start(
                List.of(connection("first"), connection("second")),
                List.of(store("ready", "first", true), store("required-failure", "second", true)),
                List.of(factory),
                ignored -> embeddingBinding()))
                .isInstanceOf(VectorTopologyInitializationException.class)
                .hasMessageContaining("required-failure")
                .hasMessageContaining("STORE_CREATION")
                .hasMessageNotContaining("secret-value");

        assertThat(closeOrder).containsExactly("second", "first");
        assertThat(factory.opened.values()).allMatch(handle -> handle.closeCount.get() == 1);
    }

    @Test
    void shouldIsolateOptionalStoreFailureWithoutFallback() {
        FakeProviderFactory factory = new FakeProviderFactory();
        factory.failingStores.add("optional-failure");

        VectorTopologyRuntime runtime = VectorTopologyBootstrap.start(
                List.of(connection("primary"), connection("optional")),
                List.of(store("ready", "primary", true), store("optional-failure", "optional", false)),
                List.of(factory),
                ignored -> embeddingBinding());

        assertThat(runtime.serviceRegistry().require(VectorStoreId.of("ready"))).isNotNull();
        assertThatThrownBy(() -> runtime.serviceRegistry().require(VectorStoreId.of("optional-failure")))
                .isInstanceOf(VectorStoreNotExistException.class);
        assertThat(runtime.degradedStores()).containsExactly(new VectorStoreStartupFailure(
                VectorStoreId.of("optional-failure"),
                VectorStoreStartupPhase.STORE_CREATION,
                IllegalStateException.class.getName()));
        assertThat(factory.opened.get("optional").closeCount.get()).isEqualTo(1);
        assertThat(factory.opened.get("primary").closeCount.get()).isZero();

        runtime.close();
        assertThat(factory.opened.get("primary").closeCount.get()).isEqualTo(1);
    }

    @Test
    void shouldCloseEveryConnectionOnceEvenWhenOneCloseFails() {
        List<String> closeOrder = new ArrayList<>();
        FakeProviderFactory factory = new FakeProviderFactory(closeOrder);
        factory.failCloseConnections.add("second");
        DefaultVectorConnectionRegistry registry = new DefaultVectorConnectionRegistry(List.of(factory));
        registry.open(connection("first"));
        registry.open(connection("second"));

        assertThatThrownBy(registry::close)
                .isInstanceOf(VectorConnectionCloseException.class)
                .hasMessage("failed to close vector connections: [second]");
        registry.close();

        assertThat(closeOrder).containsExactly("second", "first");
        assertThat(factory.opened.values()).allMatch(handle -> handle.closeCount.get() == 1);
    }

    @Test
    void shouldCreateNoResourcesForEmptyNamedTopology() {
        AtomicInteger resolverCalls = new AtomicInteger();

        VectorTopologyRuntime runtime = VectorTopologyBootstrap.start(
                List.of(),
                List.of(),
                List.of(),
                ignored -> {
                    resolverCalls.incrementAndGet();
                    return embeddingBinding();
                });

        assertThat(runtime.serviceRegistry().describeStores()).isEmpty();
        assertThat(runtime.degradedStores()).isEmpty();
        assertThat(resolverCalls).hasValue(0);
        runtime.close();
    }

    @Test
    void shouldBindDifferentEmbeddingModelsToDifferentStores() {
        FakeProviderFactory factory = new FakeProviderFactory();
        Map<String, VectorEmbeddingModelBinding> models = Map.of(
                "model-a", embeddingBinding("model-a", 1536),
                "model-b", embeddingBinding("model-b", 1536));

        VectorTopologyRuntime runtime = VectorTopologyBootstrap.start(
                List.of(connection("shared")),
                List.of(
                        store("search-a", "shared", "model-a", true),
                        store("search-b", "shared", "model-b", true)),
                List.of(factory),
                models::get);

        assertThat(factory.storeEmbeddingBindings)
                .extracting(VectorEmbeddingModelBinding::beanName)
                .containsExactly("model-a", "model-b");
        assertThat(factory.storeEmbeddingBindings)
                .extracting(VectorEmbeddingModelBinding::fingerprint)
                .doesNotHaveDuplicates();
        runtime.close();
    }

    @Test
    void shouldRejectEmbeddingDimensionMismatchBeforeCreatingStore() {
        FakeProviderFactory factory = new FakeProviderFactory();
        VectorStoreDefinition store = new VectorStoreDefinition(
                VectorStoreId.of("dimension-mismatch"),
                VectorConnectionId.of("primary"),
                "model-1536",
                "documents",
                true,
                Set.of(),
                Map.of("documents", new VectorIndexDefinition(
                        "documents", "documents", 768, "cosine", "hnsw", 1, 1, Map.of(), Map.of())));

        assertThatThrownBy(() -> VectorTopologyBootstrap.start(
                List.of(connection("primary")),
                List.of(store),
                List.of(factory),
                ignored -> embeddingBinding("model-1536", 1536)))
                .isInstanceOf(VectorTopologyInitializationException.class)
                .hasMessageContaining("EMBEDDING_MODEL")
                .hasMessageContaining(IllegalArgumentException.class.getName());

        assertThat(factory.storeEmbeddingBindings).isEmpty();
        assertThat(factory.opened.get("primary").closeCount.get()).isEqualTo(1);
    }

    @Test
    void shouldFailClosedWhenRequiredCapabilityIsNotEffective() {
        FakeProviderFactory factory = new FakeProviderFactory();
        VectorStoreDefinition store = new VectorStoreDefinition(
                VectorStoreId.of("secure-search"),
                VectorConnectionId.of("primary"),
                "aiEmbeddingModel",
                "documents",
                true,
                Set.of("ACL_SAFE_HYBRID"));

        assertThatThrownBy(() -> VectorTopologyBootstrap.start(
                List.of(connection("primary")),
                List.of(store),
                List.of(factory),
                ignored -> embeddingBinding()))
                .isInstanceOf(VectorTopologyInitializationException.class)
                .hasMessageContaining("STORE_VALIDATION");

        assertThat(factory.opened.get("primary").closeCount.get()).isEqualTo(1);
    }

    private static VectorConnectionDefinition connection(String id) {
        return new VectorConnectionDefinition(
                VectorConnectionId.of(id),
                VectorProvider.MILVUS,
                Map.of("password", "secret-value"));
    }

    private static VectorStoreDefinition store(String id, String connectionId, boolean required) {
        return store(id, connectionId, "aiEmbeddingModel", required);
    }

    private static VectorStoreDefinition store(
            String id,
            String connectionId,
            String embeddingModelRef,
            boolean required) {
        return new VectorStoreDefinition(
                VectorStoreId.of(id),
                VectorConnectionId.of(connectionId),
                embeddingModelRef,
                "documents",
                required,
                Set.of());
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "TestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "dimensions" -> 1536;
                    default -> null;
                }));
    }

    private static VectorEmbeddingModelBinding embeddingBinding() {
        return embeddingBinding("aiEmbeddingModel", 1536);
    }

    private static VectorEmbeddingModelBinding embeddingBinding(String beanName, int dimensions) {
        EmbeddingModel model = EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "EmbeddingModelTestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "dimensions" -> dimensions;
                    default -> null;
                }));
        return VectorEmbeddingModelBinding.of(beanName, model);
    }

    private static final class FakeProviderFactory implements VectorProviderFactory {

        private final AtomicInteger openCount = new AtomicInteger();
        private final Map<String, FakeConnectionHandle> opened = new LinkedHashMap<>();
        private final List<VectorConnectionHandle> storeConnections = new ArrayList<>();
        private final List<VectorEmbeddingModelBinding> storeEmbeddingBindings = new ArrayList<>();
        private final Set<String> failingStores = new java.util.LinkedHashSet<>();
        private final Set<String> failCloseConnections = new java.util.LinkedHashSet<>();
        private final List<String> closeOrder;
        private VectorStoreCapabilities effectiveCapabilities = VectorStoreCapabilities.none();

        private FakeProviderFactory() {
            this(new ArrayList<>());
        }

        private FakeProviderFactory(List<String> closeOrder) {
            this.closeOrder = closeOrder;
        }

        @Override
        public VectorProvider provider() {
            return VectorProvider.MILVUS;
        }

        @Override
        public void validateConnection(VectorConnectionDefinition definition) {
        }

        @Override
        public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        }

        @Override
        public VectorStoreCapabilities capabilities(
                VectorConnectionDefinition connection,
                VectorStoreDefinition store) {
            return effectiveCapabilities;
        }

        @Override
        public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
            openCount.incrementAndGet();
            FakeConnectionHandle handle = new FakeConnectionHandle(
                    definition.id(), definition.provider(), closeOrder,
                    failCloseConnections.contains(definition.id().value()));
            opened.put(definition.id().value(), handle);
            return handle;
        }

        @Override
        public VectorStoreHandle createStore(
                VectorConnectionHandle connection,
                VectorStoreDefinition definition,
                VectorEmbeddingModelBinding embeddingModel) {
            storeConnections.add(connection);
            storeEmbeddingBindings.add(embeddingModel);
            if (failingStores.contains(definition.id().value())) {
                throw new IllegalStateException("provider failure containing secret-value");
            }
            return VectorStoreHandle.builder(definition, provider(), proxy(VectorService.class))
                    .embeddingModel(embeddingModel)
                    .storeCapabilities(effectiveCapabilities)
                    .build();
        }
    }

    private static final class FakeConnectionHandle implements VectorConnectionHandle {

        private final VectorConnectionId id;
        private final VectorProvider provider;
        private final List<String> closeOrder;
        private final boolean failClose;
        private final AtomicInteger closeCount = new AtomicInteger();

        private FakeConnectionHandle(
                VectorConnectionId id,
                VectorProvider provider,
                List<String> closeOrder,
                boolean failClose) {
            this.id = id;
            this.provider = provider;
            this.closeOrder = closeOrder;
            this.failClose = failClose;
        }

        @Override
        public VectorConnectionId id() {
            return id;
        }

        @Override
        public VectorProvider provider() {
            return provider;
        }

        @Override
        public void close() {
            if (closeCount.incrementAndGet() > 1) {
                throw new AssertionError("connection closed more than once: " + id);
            }
            closeOrder.add(id.value());
            if (failClose) {
                throw new IllegalStateException("close failure containing secret-value");
            }
        }
    }
}
