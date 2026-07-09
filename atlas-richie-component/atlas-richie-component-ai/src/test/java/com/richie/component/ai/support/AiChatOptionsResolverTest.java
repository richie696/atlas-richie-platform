/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
