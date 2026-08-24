/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.provider.ollama;

import cn.richie696.component.http.jdk.JdkHttpAdapter;
import cn.richie696.testing.env.TestEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Ollama HTTP protocol acceptance; it is opt-in because model download is external state. */
@Tag("integration")
@EnabledIf("cn.richie696.component.ai.provider.ollama.OllamaExternalEmbeddingIT#isEnabled")
class OllamaExternalEmbeddingIT {

    static boolean isEnabled() {
        return TestEnv.isTruthy("ATLAS_AI_OLLAMA_E2E", "atlas.ai.ollama.e2e");
    }

    @Test
    void realOllamaEmbeddingEndpointReturnsVector() {
        String endpoint = TestEnv.firstResolved(
                new String[]{"ATLAS_AI_OLLAMA_ENDPOINT"},
                new String[]{"atlas.ai.ollama.endpoint"},
                OllamaImageEmbeddingAdapter.DEFAULT_BASE_URL);
        String model = TestEnv.firstResolved(
                new String[]{"ATLAS_AI_OLLAMA_MODEL"},
                new String[]{"atlas.ai.ollama.model"},
                "all-minilm");

        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                new JdkHttpAdapter(HttpClient.newHttpClient()), endpoint, model);
        var response = adapter.call(new EmbeddingRequest(List.of("atlas secret e2e"), null));

        assertThat(response.getResults()).hasSize(1);
        float[] vector = response.getResults().getFirst().getOutput();
        assertThat(vector).isNotEmpty();
        boolean hasSignal = false;
        for (float value : vector) {
            if (Math.abs(value) > 0.00001f) {
                hasSignal = true;
                break;
            }
        }
        assertThat(hasSignal).isTrue();
    }
}
