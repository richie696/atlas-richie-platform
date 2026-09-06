/*
 * Copyright (c) 2026 Richie
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.ai.provider.bailian;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BailianRerankModelTest {

    @Test
    void resolveEndpoint_mapsOpenAiCompatibleBaseUrlToDashScopeRerankEndpoint() {
        assertThat(BailianRerankModel.resolveEndpoint(
                "https://dashscope.aliyuncs.com/compatible-mode/v1"))
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
    }

    @Test
    void resolveEndpoint_preservesExplicitRerankEndpoint() {
        String endpoint = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        assertThat(BailianRerankModel.resolveEndpoint(endpoint)).isEqualTo(endpoint);
    }

    @Test
    void resolveEndpoint_usesDefaultEndpointForBlankBaseUrl() {
        assertThat(BailianRerankModel.resolveEndpoint(" "))
                .isEqualTo(BailianRerankModel.DEFAULT_BASE_URL);
    }
}
