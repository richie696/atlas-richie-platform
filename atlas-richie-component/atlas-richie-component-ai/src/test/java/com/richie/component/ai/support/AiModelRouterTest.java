package com.richie.component.ai.support;

import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.model.AiRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiModelRouterTest {

    private final AiModelRouter router = new AiModelRouter();

    @Test
    void resolveModelChain_shouldUseExplicitModelFirst() {
        AiModelProperties properties = new AiModelProperties();
        properties.getRouting().setEnabled(true);
        properties.getRouting().getSceneRules().put("code", List.of("deepseek-chat"));

        AiRequest request = new AiRequest().setModelName("gpt-4o");
        List<String> chain = router.resolveModelChain(
                request,
                "default-model",
                Map.of("gpt-4o", new Object(), "deepseek-chat", new Object()),
                properties);

        assertEquals(List.of("gpt-4o"), chain);
    }

    @Test
    void resolveModelChain_shouldUseSceneAndFallback() {
        AiModelProperties properties = new AiModelProperties();
        properties.getRouting().setEnabled(true);
        properties.getRouting().setFallbackEnabled(true);
        properties.getRouting().getSceneRules().put("code", List.of("deepseek-chat", "gpt-4o"));
        properties.getRouting().setFallbackModels(List.of("ollama-local"));

        AiRequest request = new AiRequest().setScene("code");
        List<String> chain = router.resolveModelChain(
                request,
                "default-model",
                Map.of("deepseek-chat", new Object(), "gpt-4o", new Object()),
                properties);

        assertEquals(List.of("deepseek-chat", "gpt-4o"), chain);
    }
}
