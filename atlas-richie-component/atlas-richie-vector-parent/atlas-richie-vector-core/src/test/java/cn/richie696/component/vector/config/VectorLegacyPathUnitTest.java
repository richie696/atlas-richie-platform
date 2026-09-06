/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-004 fast unit test: legacy single-Provider config (no Named Multi-store)
 * must still bind {@link VectorProperties} and report {@code hasNamedTopology() == false}
 * so the existing legacy auto-configuration path stays the single entry point.
 */
class VectorLegacyPathUnitTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "platform.component.vector.provider=qdrant",
                    "platform.component.vector.default-index=legacy-documents",
                    "platform.component.vector.qdrant.host=127.0.0.1",
                    "platform.component.vector.qdrant.port=6334",
                    "platform.component.vector.qdrant.collection=legacy-documents");

    @Test
    void legacySingleProviderConfigIsBoundAndStaysOutsideNamedTopology() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            VectorProperties properties = context.getBean(VectorProperties.class);
            assertThat(properties.getProvider().name()).isEqualTo("QDRANT");
            assertThat(properties.getDefaultIndex()).isEqualTo("legacy-documents");
            assertThat(properties.hasNamedTopology())
                    .as("a legacy single-Provider config must not activate the Named Multi-store path")
                    .isFalse();
        });
    }

    @Test
    void minimalLegacyConfigWithoutAdvancedParametersIsAccepted() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "platform.component.vector.provider=qdrant",
                        "platform.component.vector.default-index=documents")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VectorProperties properties = context.getBean(VectorProperties.class);
                    assertThat(properties.getProvider().name()).isEqualTo("QDRANT");
                    assertThat(properties.hasNamedTopology()).isFalse();
                });
    }

    @Configuration
    @EnableConfigurationProperties(VectorProperties.class)
    static class TestConfig {
    }
}
