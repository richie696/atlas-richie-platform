/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.integration;

import cn.richie696.component.vector.config.QdrantVectorAutoConfiguration;
import cn.richie696.component.vector.config.VectorAutoConfiguration;
import cn.richie696.component.vector.config.VectorProperties;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.service.VectorService;
import cn.richie696.component.vector.topology.VectorServiceRegistry;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-004: legacy zero-change upgrade regression.
 *
 * <p>Existing projects that only configure the legacy single-Provider keys
 * (no {@code platform.component.vector.connections} or {@code stores} blocks)
 * must continue to compile, start and serve the same basic
 * {@link VectorService} surface. The new Named Multi-store infrastructure
 * (Registry, VectorServiceRegistry, ProviderFactory) must stay inactive so
 * the boot path remains byte-for-byte compatible with the pre-Named
 * release.</p>
 */
class LegacyZeroChangeUpgradeRegressionTest {

    @Test
    void startsLegacyQdrantContextAndExposesOnlyTheOriginalVectorService() {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(env("VECTOR_IT_RUN", "false")),
                "set VECTOR_IT_RUN=true to execute the legacy upgrade regression");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toLowerCase(Locale.ROOT);
        String collection = "atlas_vector_legacy_q_" + suffix;
        String qdrantHost = env("VECTOR_IT_QDRANT_HOST", "127.0.0.1");
        int qdrantPort = Integer.parseInt(env("VECTOR_IT_QDRANT_PORT", "6334"));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        VectorAutoConfiguration.class,
                        QdrantVectorAutoConfiguration.class))
                .withBean("aiEmbeddingModel", EmbeddingModel.class,
                        LegacyZeroChangeUpgradeRegressionTest::syntheticEmbeddingModel)
                .withPropertyValues(
                        "platform.component.vector.provider=qdrant",
                        "platform.component.vector.default-index=" + collection,
                        "platform.component.vector.qdrant.host=" + qdrantHost,
                        "platform.component.vector.qdrant.port=" + qdrantPort,
                        "platform.component.vector.qdrant.collection=" + collection,
                        "platform.component.vector.qdrant.use-transport-layer-security=false",
                        "platform.component.vector.qdrant.initialize-schema=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VectorService.class);

                    VectorService service = context.getBean(VectorService.class);
                    assertLegacyWritesAndReads(service, collection);

                    assertThat(hasBean(context, VectorServiceRegistry.class))
                            .as("Named Multi-store must stay inactive in legacy mode")
                            .isFalse();
                    assertThat(hasBean(context, cn.richie696.component.vector.topology.VectorProviderFactory.class))
                            .as("Named ProviderFactory must not be created in legacy mode")
                            .isFalse();
                });
    }

    @Test
    void legacyContextBindsWithoutAdvancedParameters() {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(env("VECTOR_IT_RUN", "false")),
                "set VECTOR_IT_RUN=true to execute the legacy upgrade regression");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toLowerCase(Locale.ROOT);
        String collection = "atlas_vector_legacy_min_q_" + suffix;
        String qdrantHost = env("VECTOR_IT_QDRANT_HOST", "127.0.0.1");
        int qdrantPort = Integer.parseInt(env("VECTOR_IT_QDRANT_PORT", "6334"));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        VectorAutoConfiguration.class,
                        QdrantVectorAutoConfiguration.class))
                .withBean("aiEmbeddingModel", EmbeddingModel.class,
                        LegacyZeroChangeUpgradeRegressionTest::syntheticEmbeddingModel)
                .withPropertyValues(
                        "platform.component.vector.provider=qdrant",
                        "platform.component.vector.default-index=" + collection,
                        "platform.component.vector.qdrant.host=" + qdrantHost,
                        "platform.component.vector.qdrant.port=" + qdrantPort,
                        "platform.component.vector.qdrant.collection=" + collection)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VectorProperties properties = context.getBean(VectorProperties.class);
                    assertThat(properties.hasNamedTopology())
                            .as("empty legacy config must not enable the Named Multi-store path")
                            .isFalse();
                });
    }

    private static void assertLegacyWritesAndReads(VectorService service, String indexName) {
        service.upsert(VectorRecord.text(indexName, "legacy-record-1", "legacy content alpha"));
        service.upsert(VectorRecord.text(indexName, "legacy-record-2", "legacy content beta"));
        try {
            List<cn.richie696.component.vector.model.VectorSearchResult> results = service.searchByText(
                    indexName, "legacy alpha", 5, SearchOptions.builder().rerank(false).build());
            assertThat(results).extracting(cn.richie696.component.vector.model.VectorSearchResult::getId)
                    .contains("legacy-record-1");
        } finally {
            service.deleteById(indexName, "legacy-record-1");
            service.deleteById(indexName, "legacy-record-2");
        }
    }

    private static boolean hasBean(ConfigurableApplicationContext context, Class<?> type) {
        try {
            context.getBean(type);
            return true;
        } catch (NoSuchBeanDefinitionException notFound) {
            return false;
        }
    }

    private static EmbeddingModel syntheticEmbeddingModel() {
        return (EmbeddingModel) Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
                (proxy, method, args) -> {
                    Class<?> returnType = method.getReturnType();
                    if (returnType == float[].class) {
                        return new float[]{1.0f, 0.0f, 0.0f};
                    }
                    if (returnType == java.util.List.class) {
                        return List.of(new float[]{1.0f, 0.0f, 0.0f}, new float[]{0.0f, 1.0f, 0.0f});
                    }
                    if (returnType == int.class) {
                        return 3;
                    }
                    return null;
                });
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
