/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.config;

import com.richie.component.ai.support.AiChatOptionsResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.Nonnull;
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
import java.util.function.Supplier;
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
                stubMeterRegistryProvider(),
                retryTemplate
        );
        assertNotNull(factory);
    }

    private static ObjectProvider<ObservationRegistry> stubObservationProvider() {
        return new StaticObjectProvider<>(ObservationRegistry.NOOP);
    }

    private static ObjectProvider<MeterRegistry> stubMeterRegistryProvider() {
        return new StaticObjectProvider<>(new SimpleMeterRegistry());
    }

    /**
     * 最简 {@link ChatModel} 实现：固定返回预设文本，可用于单测中验证 {@link ChatClient} 调用链路。
     */
    private record StubChatModel(String content) implements ChatModel {

        @Override
        public @Nonnull ChatResponse call(@Nonnull Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }
    }

    private record StaticObjectProvider<T>(T value) implements ObjectProvider<T> {

        @Override
        public @Nonnull T getObject() {
            return value;
        }

        @Override
        public @Nonnull T getObject(@Nonnull Object... args) {
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
        public @Nonnull T getIfAvailable(@Nonnull Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public @Nonnull Stream<T> stream() {
            return Stream.of(value);
        }

        @Override
        public @Nonnull Stream<T> orderedStream() {
            return Stream.of(value);
        }
    }
}
