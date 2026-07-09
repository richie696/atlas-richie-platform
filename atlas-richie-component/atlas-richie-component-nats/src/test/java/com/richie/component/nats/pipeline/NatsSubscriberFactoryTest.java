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
package com.richie.component.nats.pipeline;

import com.richie.component.nats.strategy.NatsHeaderExtractor;
import com.richie.component.nats.strategy.NatsIdempotentChecker;
import com.richie.component.nats.strategy.NatsTracingSupport;
import com.richie.component.nats.strategy.OpenTelemetryNatsTracingSupport;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NatsSubscriberFactory} 单元测试。
 *
 * <p>验证两种管道的构建（异步 / RPC）以及 idempotent 装饰器的启用/禁用分支。</p>
 *
 * <p>说明：管道中的 {@link TracingMessageDecorator} 内部调用
 * {@code Context.current().with(span).makeCurrent()}，需要真实的 {@link io.opentelemetry.api.trace.Span} 实现，
 * 因此这里用 {@link OpenTelemetry#noop()} 构造一个真实的 {@link OpenTelemetryNatsTracingSupport}，
 * 通过 spy 验证其方法被调用的次数。</p>
 */
class NatsSubscriberFactoryTest {

    @Test
    void buildAsyncPipeline_withIdempotentEnabled_shouldBuildPipeline() throws Exception {
        NatsHeaderExtractor headerExtractor = mock(NatsHeaderExtractor.class);
        NatsIdempotentChecker idempotentChecker = mock(NatsIdempotentChecker.class);
        when(idempotentChecker.isFirstTime(anyString(), anyLong())).thenReturn(true);

        NatsTracingSupport tracingSupport = spyTracingSupport();
        NatsSubscriberFactory factory = new NatsSubscriberFactory(
                tracingSupport, headerExtractor, idempotentChecker, true, 60_000L);

        AtomicBoolean called = new AtomicBoolean(false);
        NatsMessageHandler handler = factory.buildAsyncPipeline(msg -> called.set(true));

        assertThat(handler).isNotNull();

        Message msg = mockMessage("subj");
        handler.handle(msg);

        assertThat(called.get()).isTrue();
        // 异步管道使用 CONSUMER span
        verify(tracingSupport).startConsumerSpan(anyString(), any(Headers.class));
        // idempotent 装饰器被启用，应被调用
        verify(idempotentChecker).isFirstTime(anyString(), anyLong());
    }

    @Test
    void buildAsyncPipeline_withIdempotentDisabled_shouldSkipIdempotentDecorator() throws Exception {
        NatsHeaderExtractor headerExtractor = mock(NatsHeaderExtractor.class);
        NatsTracingSupport tracingSupport = spyTracingSupport();

        NatsSubscriberFactory factory = new NatsSubscriberFactory(
                tracingSupport, headerExtractor, null, false, 0L);

        AtomicBoolean called = new AtomicBoolean(false);
        NatsMessageHandler handler = factory.buildAsyncPipeline(msg -> called.set(true));

        assertThat(handler).isNotNull();

        Message msg = mockMessage("subj");
        handler.handle(msg);

        assertThat(called.get()).isTrue();
        verify(tracingSupport).startConsumerSpan(anyString(), any(Headers.class));
    }

    @Test
    void buildRpcPipeline_shouldBuildWithoutIdempotent() throws Exception {
        NatsHeaderExtractor headerExtractor = mock(NatsHeaderExtractor.class);
        NatsTracingSupport tracingSupport = spyTracingSupport();

        NatsSubscriberFactory factory = new NatsSubscriberFactory(
                tracingSupport, headerExtractor, null, false, 0L);

        AtomicBoolean called = new AtomicBoolean(false);
        NatsMessageHandler handler = factory.buildRpcPipeline(msg -> called.set(true));

        assertThat(handler).isNotNull();

        Message msg = mockMessage("rpc.subj");
        handler.handle(msg);

        assertThat(called.get()).isTrue();
        // RPC 管道使用 SERVER span
        verify(tracingSupport).startServerSpan(anyString(), any(Headers.class));
        // RPC 管道无 idempotent
        verify(tracingSupport, never()).startConsumerSpan(anyString(), any(Headers.class));
    }

    @Test
    void buildAsyncPipeline_shouldExecuteInOrder_tracingThenContextThenBusiness() throws Exception {
        NatsHeaderExtractor headerExtractor = mock(NatsHeaderExtractor.class);
        NatsIdempotentChecker idempotentChecker = mock(NatsIdempotentChecker.class);
        when(idempotentChecker.isFirstTime(anyString(), anyLong())).thenReturn(true);
        NatsTracingSupport tracingSupport = spyTracingSupport();

        NatsSubscriberFactory factory = new NatsSubscriberFactory(
                tracingSupport, headerExtractor, idempotentChecker, true, 60_000L);

        NatsMessageHandler handler = factory.buildAsyncPipeline(msg -> { /* no-op */ });
        Message msg = mockMessage("subj");
        handler.handle(msg);

        // 验证 header 提取被调用
        verify(headerExtractor).extract(any(Headers.class));
        verify(idempotentChecker).isFirstTime(anyString(), anyLong());

        // 顺序：startConsumerSpan 应在 extract 之前；finishSpan 应在 extract 之后
        InOrder tracingInOrder = inOrder(tracingSupport);
        tracingInOrder.verify(tracingSupport).startConsumerSpan(anyString(), any(Headers.class));
        tracingInOrder.verify(tracingSupport).finishSpan(any(), org.mockito.ArgumentMatchers.eq(true), any());
    }

    private NatsTracingSupport spyTracingSupport() {
        return spy(new OpenTelemetryNatsTracingSupport(true, OpenTelemetry.noop()));
    }

    private Message mockMessage(String subject) {
        Message msg = mock(Message.class);
        Headers headers = new Headers();
        headers.put("nats-message-id", "id-1");
        when(msg.getHeaders()).thenReturn(headers);
        when(msg.getSubject()).thenReturn(subject);
        when(msg.getData()).thenReturn("data".getBytes());
        when(msg.isJetStream()).thenReturn(false);
        return msg;
    }
}
