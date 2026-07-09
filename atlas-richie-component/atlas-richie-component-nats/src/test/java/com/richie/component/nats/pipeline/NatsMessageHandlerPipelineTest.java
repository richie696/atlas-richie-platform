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
package com.richie.component.nats.pipeline;

import io.nats.client.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link NatsMessageHandlerPipeline} 单元测试
 */
class NatsMessageHandlerPipelineTest {

    @Test
    void build_withNoDecorators_shouldReturnOriginalHandler() throws Exception {
        List<String> log = new ArrayList<>();
        NatsMessageHandler business = msg -> log.add("business");

        NatsMessageHandler result = new NatsMessageHandlerPipeline().build(business);
        result.handle(mock(Message.class));

        assertThat(log).containsExactly("business");
    }

    @Test
    void build_withSingleDecorator_shouldWrapHandler() throws Exception {
        List<String> log = new ArrayList<>();

        NatsMessageHandler business = msg -> log.add("business");
        NatsMessageHandlerPipeline pipeline = new NatsMessageHandlerPipeline()
                .addDecorator(inner -> msg -> {
                    log.add("decorator-before");
                    inner.handle(msg);
                    log.add("decorator-after");
                });

        NatsMessageHandler result = pipeline.build(business);
        result.handle(mock(Message.class));

        assertThat(log).containsExactly("decorator-before", "business", "decorator-after");
    }

    @Test
    void build_withMultipleDecorators_shouldWrapInCorrectOrder() throws Exception {
        List<String> log = new ArrayList<>();

        NatsMessageHandler business = msg -> log.add("business");

        NatsMessageHandlerPipeline pipeline = new NatsMessageHandlerPipeline()
                .addDecorator(inner -> msg -> {
                    log.add("outer-before");
                    inner.handle(msg);
                    log.add("outer-after");
                })
                .addDecorator(inner -> msg -> {
                    log.add("inner-before");
                    inner.handle(msg);
                    log.add("inner-after");
                });

        NatsMessageHandler result = pipeline.build(business);
        result.handle(mock(Message.class));

        // First decorator is outermost, second is innermost
        assertThat(log).containsExactly(
                "outer-before",
                "inner-before",
                "business",
                "inner-after",
                "outer-after"
        );
    }

    @Test
    void build_decoratorCanShortCircuit() throws Exception {
        List<String> log = new ArrayList<>();

        NatsMessageHandler business = msg -> log.add("business");

        NatsMessageHandlerPipeline pipeline = new NatsMessageHandlerPipeline()
                .addDecorator(inner -> msg -> {
                    // Short circuit: do NOT call inner.handle()
                    log.add("short-circuit");
                });

        NatsMessageHandler result = pipeline.build(business);
        result.handle(mock(Message.class));

        assertThat(log).containsExactly("short-circuit");
    }

    @Test
    void build_withThreeDecorators_shouldMaintainOrder() throws Exception {
        List<String> log = new ArrayList<>();

        NatsMessageHandler business = msg -> log.add("core");

        NatsMessageHandlerPipeline pipeline = new NatsMessageHandlerPipeline()
                .addDecorator(inner -> msg -> {
                    log.add("A");
                    inner.handle(msg);
                })
                .addDecorator(inner -> msg -> {
                    log.add("B");
                    inner.handle(msg);
                })
                .addDecorator(inner -> msg -> {
                    log.add("C");
                    inner.handle(msg);
                });

        NatsMessageHandler result = pipeline.build(business);
        result.handle(mock(Message.class));

        assertThat(log).containsExactly("A", "B", "C", "core");
    }
}
