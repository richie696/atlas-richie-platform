/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.example;

import com.richie.component.ai.model.AiHealthResult;
import com.richie.component.ai.model.AiModelInfo;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.model.AiStreamChunk;
import com.richie.component.ai.model.ModelOptions;
import com.richie.component.ai.service.AiChatService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI组件使用示例单元测试
 * 通过测试用例展示推荐调用方式，避免示例类进入业务打包产物
 *
 * @author richie696
 * @version 1.0
 * @since 2026-04-22
 */
class AiModelUsageExampleTest {

    private final AiChatService aiModelService = new MockAiChatService();

    @Test
    void shouldCallWithDefaultModel() {
        AiRequest request = AiRequest.ofUserMessage("你好，请介绍一下自己");
        AiResponse response = aiModelService.call(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getContent());
        assertEquals("mock-default", response.getModelName());
    }

    @Test
    void shouldCallWithSpecifiedModel() {
        AiRequest request = AiRequest.ofUserMessage("请解释什么是人工智能");
        AiResponse response = aiModelService.callWithModel("gpt-4", request);

        assertTrue(response.isSuccess());
        assertEquals("gpt-4", response.getModelName());
    }

    @Test
    void shouldCallAsyncSuccessfully() {
        AiRequest request = AiRequest.ofUserMessage("请生成一个Java Hello World程序");
        CompletableFuture<AiResponse> future = aiModelService.callAsync(request);
        AiResponse response = future.join();

        assertTrue(response.isSuccess());
        assertNotNull(response.getContent());
    }

    @Test
    void shouldBuildConversationRequest() {
        AiRequest request = AiRequest.ofSystemAndUser(
                "你是一个专业的Java开发工程师",
                "请解释Spring Boot的自动配置原理"
        );

        assertNotNull(request.getMessages());
        assertEquals(2, request.getMessages().size());
        assertEquals("system", request.getMessages().get(0).getRole());
        assertEquals("user", request.getMessages().get(1).getRole());
    }

    @Test
    void shouldReturnModelMetadata() {
        List<AiModelInfo> models = aiModelService.getAvailableModels();
        assertFalse(models.isEmpty());
        assertTrue(aiModelService.isModelAvailable("gpt-4"));

        AiModelInfo modelInfo = aiModelService.getModelInfo("gpt-4");
        assertNotNull(modelInfo);
        assertEquals("gpt-4", modelInfo.getName());
    }

    private static class MockAiChatService implements AiChatService {

        @Override
        public AiResponse call(AiRequest request) {
            return AiResponse.success("mock-response", "mock-default", "OPENAI");
        }

        @Override
        public CompletableFuture<AiResponse> callAsync(AiRequest request) {
            return CompletableFuture.completedFuture(call(request));
        }

        @Override
        public AiResponse callWithModel(String modelName, AiRequest request) {
            return AiResponse.success("mock-response", modelName, "OPENAI");
        }

        @Override
        public List<AiModelInfo> getAvailableModels() {
            return List.of(
                    AiModelInfo.available("gpt-4", "OPENAI"),
                    AiModelInfo.available("deepseek-chat", "DEEPSEEK")
            );
        }

        @Override
        public AiModelInfo getModelInfo(String modelName) {
            return AiModelInfo.available(modelName, "OPENAI");
        }

        @Override
        public boolean isModelAvailable(String modelName) {
            return true;
        }

        @Override
        public String getDefaultModel() {
            return "mock-default";
        }

        @Override
        public void setDefaultModel(String modelName) {
            // no-op for mock
        }

        @Override
        public void initializeModels(List<ModelOptions> modelOptionsList) {
            // no-op for mock
        }

        @Override
        public Flux<AiStreamChunk> stream(AiRequest request) {
            return Flux.just(
                    AiStreamChunk.delta("mock-response", "mock-default", "OPENAI"),
                    AiStreamChunk.finished("mock-default", "OPENAI", null)
            );
        }

        @Override
        public void removeModel(String modelName) {
            // no-op for mock
        }

        @Override
        public AiHealthResult probe(String modelName) {
            return AiHealthResult.healthy(modelName, "OPENAI", false, 0L);
        }

        @Override
        public List<AiHealthResult> probeAll() {
            return List.of(AiHealthResult.healthy("mock-default", "OPENAI", false, 0L));
        }
    }
}

