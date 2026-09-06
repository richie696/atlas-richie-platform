/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.provider.bailian;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BailianTextEmbeddingAdapterTest {

    @Test
    void resolveEndpoint_keepsExistingEmbeddingsSuffix() {
        assertThat(BailianTextEmbeddingAdapter.resolveEndpoint(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");
    }

    @Test
    void resolveEndpoint_appendsEmbeddingsSuffixToDashScopeOpenAiBaseUrl() {
        assertThat(BailianTextEmbeddingAdapter.resolveEndpoint(
                "https://dashscope.aliyuncs.com/compatible-mode/v1"))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");
    }

    @Test
    void resolveEndpoint_fallsBackToDefaultForBlankBaseUrl() {
        assertThat(BailianTextEmbeddingAdapter.resolveEndpoint(" "))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");
    }

    @Test
    void resolveEndpoint_stripsTrailingSlash() {
        assertThat(BailianTextEmbeddingAdapter.resolveEndpoint(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/"))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");
    }

    @Test
    void resolveConfiguredDimensions_usesDefaultForNull() {
        assertThat(BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v3", null))
                .isEqualTo(BailianTextEmbeddingAdapter.DEFAULT_DIMENSIONS);
    }

    @Test
    void resolveConfiguredDimensions_acceptsValidV3Dimension() {
        assertThat(BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v3", 1024))
                .isEqualTo(1024);
        assertThat(BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v3", 512))
                .isEqualTo(512);
        assertThat(BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v3", 64))
                .isEqualTo(64);
    }

    @Test
    void resolveConfiguredDimensions_rejectsBelowMinForV3() {
        assertThatThrownBy(() -> BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v3", 56))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple of 8");
    }

    @Test
    void resolveConfiguredDimensions_rejectsAboveMaxForV3() {
        assertThatThrownBy(() -> BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v3", 2048))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 64 and 1024");
    }

    @Test
    void resolveConfiguredDimensions_rejectsNonMultipleOfEight() {
        assertThatThrownBy(() -> BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v3", 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveConfiguredDimensions_passesThroughForNonV3Models() {
        assertThat(BailianTextEmbeddingAdapter.resolveConfiguredDimensions("text-embedding-v2", 1536))
                .isEqualTo(1536);
    }
}
