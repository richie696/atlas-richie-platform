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
package com.richie.component.ai.integration;

import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.service.AiModelService;
import com.richie.component.ai.support.AiIntegrationTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AiIntegrationTestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "platform.component.ai.config-initialization-enabled=false"
})
class AiModelServiceIT {

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private Map<String, ChatClient> chatClients;

    @BeforeEach
    void registerStubModel() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("it-hello"))));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("it-hello");
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);

        chatClients.clear();
        chatClients.put("it-stub", chatClient);
    }

    @Test
    void call_shouldUseSpringContextAndDynamicModel() {
        AiRequest request = new AiRequest()
                .setModelName("it-stub")
                .setMessages(List.of(new AiRequest.Message().setRole("user").setContent("ping")));

        AiResponse response = aiModelService.call(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("it-hello");
        assertThat(aiModelService.isModelAvailable("it-stub")).isTrue();
    }
}
