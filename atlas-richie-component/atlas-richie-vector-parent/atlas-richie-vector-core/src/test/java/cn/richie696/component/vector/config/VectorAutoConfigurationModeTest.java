/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.diagnostics.VectorStoreDiagnostics;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorContent;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.observation.VectorStoreObservationEvent;
import cn.richie696.component.vector.observation.VectorStoreObservationHook;
import cn.richie696.component.vector.observation.MicrometerVectorStoreObservationHook;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorProviderFactory;
import cn.richie696.component.vector.topology.VectorServiceRegistry;
import cn.richie696.component.vector.topology.VectorStoreCapabilities;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorTopologyRuntime;
import cn.richie696.component.vector.topology.AuthorizedVectorStoreResolver;
import cn.richie696.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class VectorAutoConfigurationModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VectorAutoConfiguration.class));

    @Test
    void shouldStayInactiveWithoutLegacyOrNamedConfiguration() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(VectorProperties.class);
            assertThat(context).doesNotHaveBean(VectorStoreTopologyValidator.class);
        });
    }

    @Test
    void shouldKeepLegacyProviderModeActiveWithoutFactories() {
        runner.withPropertyValues("platform.component.vector.provider=milvus")
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorProperties.class);
                    assertThat(context).hasSingleBean(VectorStoreTopologyValidator.class);
                    assertThat(context).hasSingleBean(VectorMultiProviderGuard.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldActivateNamedModeWithoutLegacyProviderProperty() {
        runner.withUserConfiguration(FakeFactoryConfiguration.class)
                .withPropertyValues(
                        "platform.component.vector.connections.primary.provider=milvus",
                        "platform.component.vector.connections.primary.settings.host=primary.invalid",
                        "platform.component.vector.stores.primary.connection-ref=primary")
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorProperties.class);
                    assertThat(context).hasSingleBean(VectorStoreTopologyValidator.class);
                    assertThat(context.getBean(VectorProperties.class).hasNamedTopology()).isTrue();
                    assertThat(context).hasSingleBean(VectorTopologyRuntime.class);
                    assertThat(context).hasSingleBean(VectorServiceRegistry.class);
                    assertThat(context).hasSingleBean(VectorStoreDiagnostics.class);
                    assertThat(context.getBean(VectorServiceRegistry.class).describeStores()).hasSize(1);
                    assertThat(context).hasSingleBean(VectorService.class);
                    assertThat(context.getBean(VectorService.class)).isSameAs(
                            context.getBean(VectorServiceRegistry.class)
                                    .require(cn.richie696.component.vector.topology.VectorStoreId.of("primary"))
                                    .service());
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldNotCreateAmbiguousLegacyServiceForMultipleNamedStores() {
        runner.withUserConfiguration(FakeFactoryConfiguration.class)
                .withPropertyValues(
                        "platform.component.vector.connections.primary.provider=milvus",
                        "platform.component.vector.connections.primary.settings.host=primary.invalid",
                        "platform.component.vector.stores.documents.connection-ref=primary",
                        "platform.component.vector.stores.prompts.connection-ref=primary")
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorServiceRegistry.class);
                    assertThat(context).hasSingleBean(VectorStoreDiagnostics.class);
                    assertThat(context.getBean(VectorServiceRegistry.class).describeStores()).hasSize(2);
                    assertThat(context).doesNotHaveBean(VectorService.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldApplyOptionalObservationHookToNamedStore() {
        ArrayList<VectorStoreObservationEvent> events = new ArrayList<>();
        runner.withUserConfiguration(FakeFactoryConfiguration.class)
                .withBean(VectorStoreObservationHook.class, () -> events::add)
                .withPropertyValues(
                        "platform.component.vector.connections.primary.provider=milvus",
                        "platform.component.vector.connections.primary.settings.host=primary.invalid",
                        "platform.component.vector.stores.primary.connection-ref=primary")
                .run(context -> {
                    context.getBean(VectorService.class).searchByText("documents", "secret query", 1, null);
                    assertThat(events).singleElement().satisfies(event -> {
                        assertThat(event.storeId().value()).isEqualTo("primary");
                        assertThat(event.toString()).doesNotContain("secret query");
                    });
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldAutoConfigureMicrometerObservationBridgeWhenRegistriesExist() {
        runner.withUserConfiguration(FakeFactoryConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .withPropertyValues(
                        "platform.component.vector.connections.primary.provider=milvus",
                        "platform.component.vector.connections.primary.settings.host=primary.invalid",
                        "platform.component.vector.stores.primary.connection-ref=primary")
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorStoreObservationHook.class);
                    assertThat(context.getBean(VectorStoreObservationHook.class))
                            .isInstanceOf(MicrometerVectorStoreObservationHook.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldExposeLegacyServiceAsDefaultRegistryStoreWithoutDuplicatingIt(CapturedOutput output) {
        runner.withUserConfiguration(LegacyServiceConfiguration.class)
                .withPropertyValues("platform.component.vector.provider=milvus")
                .run(context -> {
                    assertThat(context).hasSingleBean(VectorService.class);
                    assertThat(context).hasSingleBean(VectorServiceRegistry.class);
                    assertThat(context).hasSingleBean(VectorStoreDiagnostics.class);
                    var registry = context.getBean(VectorServiceRegistry.class);
                    assertThat(registry.describeStores()).singleElement().satisfies(descriptor -> {
                        assertThat(descriptor.id().value()).isEqualTo("default");
                        assertThat(descriptor.connectionId().value()).isEqualTo("default");
                        assertThat(descriptor.effectiveCapabilities()).isEmpty();
                    });
                    assertThat(registry.require(cn.richie696.component.vector.topology.VectorStoreId.of("default")).service())
                            .isSameAs(context.getBean(VectorService.class));
                    VectorService legacyApi = context.getBean(VectorService.class);
                    VectorService defaultStoreApi = registry.require(
                            cn.richie696.component.vector.topology.VectorStoreId.of("default")).service();
                    VectorRecord record = new VectorRecord()
                            .setId("synthetic-vector-1")
                            .setIndexName("documents")
                            .setContent(new VectorContent.TextContent("synthetic content", "text/plain"));
                    assertThat(legacyApi.upsert(record)).isEqualTo("synthetic-vector-1");
                    assertThat(defaultStoreApi.upsert(record)).isEqualTo("synthetic-vector-1");
                    List<VectorSearchResult> legacyResults = legacyApi.searchByText(
                            "documents", "synthetic query", 3, SearchOptions.builder().rerank(false).build());
                    List<VectorSearchResult> defaultStoreResults = defaultStoreApi.searchByText(
                            "documents", "synthetic query", 3, SearchOptions.builder().rerank(false).build());
                    assertThat(defaultStoreResults).usingRecursiveComparison().isEqualTo(legacyResults);
                    assertThat(context).hasSingleBean(AuthorizedVectorStoreResolver.class);
                    assertThat(context.getBean(VectorStoreDiagnostics.class).checkAll().stores())
                            .singleElement()
                            .satisfies(snapshot -> assertThat(snapshot.storeId().value()).isEqualTo("default"));
                    assertThat(output).contains("Legacy single-provider vector configuration is deprecated")
                            .doesNotContain("primary.invalid");
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldFailNamedModeWhenFactoryIsMissing() {
        runner.withPropertyValues(
                        "platform.component.vector.connections.primary.provider=milvus",
                        "platform.component.vector.stores.primary.connection-ref=primary")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("vector provider factory is not available: MILVUS");
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeFactoryConfiguration {

        @Bean("aiEmbeddingModel")
        EmbeddingModel aiEmbeddingModel() {
            return proxy(EmbeddingModel.class);
        }

        @Bean
        VectorProviderFactory vectorProviderFactory() {
            return new VectorProviderFactory() {
                @Override
                public VectorProvider provider() {
                    return VectorProvider.MILVUS;
                }

                @Override
                public void validateConnection(VectorConnectionDefinition definition) {
                    if (!definition.settings().containsKey("host")) {
                        throw new IllegalArgumentException("host is required");
                    }
                }

                @Override
                public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
                }

                @Override
                public VectorStoreCapabilities capabilities(
                        VectorConnectionDefinition connection,
                        VectorStoreDefinition store) {
                    return VectorStoreCapabilities.none();
                }

                @Override
                public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
                    return new VectorConnectionHandle() {
                        @Override
                        public cn.richie696.component.vector.topology.VectorConnectionId id() {
                            return definition.id();
                        }

                        @Override
                        public VectorProvider provider() {
                            return definition.provider();
                        }

                        @Override
                        public void close() {
                        }
                    };
                }

                @Override
                public VectorStoreHandle createStore(
                        VectorConnectionHandle connection,
                        VectorStoreDefinition definition,
                        VectorEmbeddingModelBinding embeddingModel) {
                    return VectorStoreHandle.builder(definition, provider(), proxy(VectorService.class))
                            .embeddingModel(embeddingModel)
                            .storeCapabilities(capabilities(null, definition))
                            .build();
                }
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LegacyServiceConfiguration {

        @Bean("aiEmbeddingModel")
        EmbeddingModel aiEmbeddingModel() {
            return proxy(EmbeddingModel.class);
        }

        @Bean
        VectorService legacyVectorService() {
            return legacyVectorServiceProxy();
        }
    }

    private static VectorService legacyVectorServiceProxy() {
        return (VectorService) Proxy.newProxyInstance(
                VectorService.class.getClassLoader(),
                new Class<?>[]{VectorService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "LegacyVectorServiceTestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "upsert" -> ((VectorRecord) args[0]).getId();
                    case "searchByText" -> List.of(VectorSearchResult.of(
                            "synthetic-hit-1", args[1].toString(), 0.75D));
                    case "searchByImage" -> List.of();
                    case "upsertAll", "deleteAll" -> reactor.core.publisher.Flux.empty();
                    default -> null;
                });
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
}
