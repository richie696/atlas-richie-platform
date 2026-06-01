package com.richie.component.ai.config;

import com.richie.component.ai.support.AiChatOptionsResolver;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 {@link AiChatClientFactory} 与 Spring AI 2.x 新增 API（ChatModel 注入、Spring 7 内建 RetryTemplate）的集成。
 * <p>
 * 使用本地 {@link StubChatModel} 模拟模型响应（不发起任何真实 LLM 调用），
 * 可在单元测试阶段验证调用链路的连接性与构造器签名的正确性。
 * <p>
 * 注意：M8 的重试类型为 Spring 7 的 {@link org.springframework.core.retry.RetryTemplate}，
 * 取代旧版 {@code org.springframework.retry.support.RetryTemplate}（来自 spring-retry 库）。
 * M8 的 {@code spring-ai-test} 不再提供 {@code MockChatModel}，本测试自维护最简实现。
 */
class AiChatClientFactoryTest {

    @Test
    void chatClient_builtOnStubModel_shouldReturnConfiguredResponse() {
        ChatModel stubChatModel = new StubChatModel("mocked-reply");
        ChatClient chatClient = ChatClient.builder(stubChatModel).build();

        String content = chatClient.prompt()
                .user("ping")
                .call()
                .content();

        assertEquals("mocked-reply", content);
    }

    @Test
    void factory_shouldBeConstructibleWithOptionalObservationAndRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate(
                RetryPolicy.builder().maxRetries(1).build()
        );
        AiChatClientFactory factory = new AiChatClientFactory(
                new AiChatOptionsResolver(),
                stubObservationProvider(),
                retryTemplate
        );
        assertNotNull(factory);
    }

    private static ObjectProvider<ObservationRegistry> stubObservationProvider() {
        return new StaticObjectProvider<>(ObservationRegistry.NOOP);
    }

    /**
     * 最简 {@link ChatModel} 实现：固定返回预设文本，可用于单测中验证 {@link ChatClient} 调用链路。
     */
    private static final class StubChatModel implements ChatModel {

        private final String content;

        private StubChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }
    }

    private static final class StaticObjectProvider<T> implements ObjectProvider<T> {

        private final T value;

        private StaticObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getIfAvailable(java.util.function.Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public Stream<T> stream() {
            return Stream.of(value);
        }

        @Override
        public Stream<T> orderedStream() {
            return Stream.of(value);
        }
    }
}
