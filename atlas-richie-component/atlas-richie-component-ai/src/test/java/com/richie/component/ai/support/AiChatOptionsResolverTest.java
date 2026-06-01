package com.richie.component.ai.support;

import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.model.AiRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiChatOptionsResolverTest {

    private final AiChatOptionsResolver resolver = new AiChatOptionsResolver();

    @Test
    void mergeOptions_shouldOverrideNonNullRequestFields() {
        AiModelProperties.AiModelOptions base = new AiModelProperties.AiModelOptions()
                .setTemperature(0.7)
                .setMaxTokens(2000);

        AiRequest.ModelOptions request = new AiRequest.ModelOptions()
                .setTemperature(0.2)
                .setTopP(0.9);

        AiModelProperties.AiModelOptions merged = resolver.mergeOptions(base, request);

        assertEquals(0.2, merged.getTemperature());
        assertEquals(2000, merged.getMaxTokens());
        assertEquals(0.9, merged.getTopP());
    }

    @Test
    void toChatOptions_shouldBuildOpenAiOptions() {
        AiModelProperties.AiModelOptions options = new AiModelProperties.AiModelOptions()
                .setModel("gpt-4o")
                .setTemperature(0.5);

        var chatOptions = resolver.toOpenAiChatOptions(options);
        assertEquals("gpt-4o", chatOptions.getModel());
        assertEquals(0.5, chatOptions.getTemperature());
    }
}
