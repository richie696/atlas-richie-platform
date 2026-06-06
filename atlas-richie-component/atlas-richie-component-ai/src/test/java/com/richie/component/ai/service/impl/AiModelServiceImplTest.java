package com.richie.component.ai.service.impl;

import com.richie.component.ai.config.AiChatClientFactory;
import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.model.ModelOptions;
import com.richie.component.ai.support.AiChatOptionsResolver;
import com.richie.component.ai.support.AiModelCircuitBreaker;
import com.richie.component.ai.support.AiModelRouter;
import com.richie.component.ai.support.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelServiceImplTest {

    @Mock
    private AiModelProperties aiModelProperties;

    @Mock
    private AiChatClientFactory aiChatClientFactory;

    private final AiChatOptionsResolver optionsResolver = new AiChatOptionsResolver();
    private final AiModelRouter aiModelRouter = new AiModelRouter();
    private final AiModelCircuitBreaker circuitBreaker = new AiModelCircuitBreaker();
    private final ToolRegistry toolRegistry = new ToolRegistry(List.of());

    private Map<String, ChatClient> chatClients;
    private AiModelServiceImpl service;

    @BeforeEach
    void setUp() {
        chatClients = new ConcurrentHashMap<>();
        service = new AiModelServiceImpl(
                chatClients,
                aiModelProperties,
                aiChatClientFactory,
                optionsResolver,
                aiModelRouter,
                circuitBreaker,
                toolRegistry
        );
    }

    @Test
    void call_whenNotInitialized_shouldReturnFailure() {
        AiResponse response = service.call(new AiRequest().setMessages(List.of(
                new AiRequest.Message().setRole("user").setContent("hi"))));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("MODEL_NOT_INITIALIZED");
    }

    @Test
    void call_withStubClient_shouldReturnSuccess() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("mocked-reply"))));

        when(aiChatClientFactory.createChatClients(any(List.class)))
                .thenReturn(new HashMap<>(Map.of("stub", chatClient)));
        AiModelProperties.AiModel aiModel = new AiModelProperties.AiModel();
        aiModel.setProvider(AiModelProperties.AiProviderType.OPENAI);
        when(aiChatClientFactory.toAiModel(any(ModelOptions.class))).thenReturn(aiModel);
        when(aiModelProperties.getResilience()).thenReturn(new AiModelProperties.ResilienceConfig());
        when(aiModelProperties.getRouting()).thenReturn(new AiModelProperties.RoutingConfig());
        when(aiModelProperties.isConfigInitializationEnabled()).thenReturn(false);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("mocked-reply");
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);

        service.initializeModels(List.of(new ModelOptions().setModelName("stub").setProvider("OPENAI")));

        AiRequest request = new AiRequest()
                .setModelName("stub")
                .setMessages(List.of(new AiRequest.Message().setRole("user").setContent("ping")));

        AiResponse response = service.call(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("mocked-reply");
        assertThat(response.getModelName()).isEqualTo("stub");
    }

    @Test
    void initializeModels_shouldRegisterDynamicClients() {
        ChatClient dynamicClient = mock(ChatClient.class);
        Map<String, ChatClient> dynamic = Map.of("dyn", dynamicClient);
        when(aiChatClientFactory.createChatClients(any(List.class))).thenReturn(new HashMap<>(dynamic));
        when(aiChatClientFactory.toAiModel(any(ModelOptions.class))).thenReturn(new AiModelProperties.AiModel());

        service.initializeModels(List.of(new ModelOptions().setModelName("dyn")));

        assertThat(service.isModelAvailable("dyn")).isTrue();
        assertThat(service.getDefaultModel()).isEqualTo("dyn");
    }
}
