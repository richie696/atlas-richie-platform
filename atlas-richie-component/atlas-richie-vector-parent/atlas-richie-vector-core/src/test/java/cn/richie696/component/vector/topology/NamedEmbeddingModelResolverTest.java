/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.model.Modality;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamedEmbeddingModelResolverTest {

    @Test
    void shouldResolveExactBeanNameAndBuildStableRedactedFingerprint() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        EmbeddingModel model = embeddingModel(1536);
        beanFactory.registerSingleton("model-a", model);
        SpringNamedEmbeddingModelResolver resolver = new SpringNamedEmbeddingModelResolver(beanFactory);

        VectorEmbeddingModelBinding first = resolver.resolve("model-a");
        VectorEmbeddingModelBinding second = resolver.resolve("model-a");

        assertThat(first.model()).isSameAs(model);
        assertThat(first.dimensions()).isEqualTo(1536);
        assertThat(first.normalization()).isEqualTo(EmbeddingNormalization.UNSPECIFIED);
        assertThat(first.modalities()).containsExactly(Modality.TEXT);
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint()).hasSize(24);
        assertThat(first.fingerprint()).doesNotContain("model-a");
    }

    @Test
    void shouldNotFallbackWhenConfiguredBeanIsMissingOrWrongType() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("aiEmbeddingModel", embeddingModel(1536));
        beanFactory.registerSingleton("wrong-type", "not-an-embedding-model");
        SpringNamedEmbeddingModelResolver resolver = new SpringNamedEmbeddingModelResolver(beanFactory);

        assertThatThrownBy(() -> resolver.resolve("missing-model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding model bean is not available: missing-model");
        assertThatThrownBy(() -> resolver.resolve("wrong-type"))
                .isInstanceOf(org.springframework.beans.factory.BeanNotOfRequiredTypeException.class);
    }

    @Test
    void shouldProduceDifferentFingerprintsForDifferentBindings() {
        VectorEmbeddingModelBinding first = VectorEmbeddingModelBinding.of("model-a", embeddingModel(768));
        VectorEmbeddingModelBinding second = VectorEmbeddingModelBinding.of("model-b", embeddingModel(768));
        VectorEmbeddingModelBinding third = VectorEmbeddingModelBinding.of("model-a", embeddingModel(1536));

        assertThat(java.util.Set.of(first.fingerprint(), second.fingerprint(), third.fingerprint())).hasSize(3);
    }

    @Test
    void shouldIncludeNormalizationAndModalitiesInRedactedFingerprint() {
        VectorEmbeddingModelBinding base = VectorEmbeddingModelBinding.of("model-a", embeddingModel(768));
        VectorEmbeddingModelBinding multimodal = base.withContract(
                EmbeddingNormalization.UNIT_L2,
                java.util.Set.of(Modality.TEXT, Modality.IMAGE));

        assertThat(multimodal.model()).isSameAs(base.model());
        assertThat(multimodal.dimensions()).isEqualTo(768);
        assertThat(multimodal.normalization()).isEqualTo(EmbeddingNormalization.UNIT_L2);
        assertThat(multimodal.modalities()).containsExactlyInAnyOrder(Modality.TEXT, Modality.IMAGE);
        assertThat(multimodal.fingerprint()).hasSize(24).isNotEqualTo(base.fingerprint());
        assertThat(multimodal.fingerprint()).doesNotContain("model-a");
        assertThat(multimodal.toString()).contains("model=<redacted>").doesNotContain("EmbeddingModelTestProxy");
    }

    private static EmbeddingModel embeddingModel(int dimensions) {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "EmbeddingModelTestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "dimensions" -> dimensions;
                    default -> null;
                }));
    }
}
