package com.richie.component.ai.example;

import com.richie.component.ai.model.AiModelInfo;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.model.ModelOptions;
import com.richie.component.ai.service.AiModelService;
import org.junit.jupiter.api.Test;

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

    private final AiModelService aiModelService = new MockAiModelService();

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

    private static class MockAiModelService implements AiModelService {

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
    }
}

