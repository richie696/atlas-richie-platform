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

import com.richie.component.nats.NatsConstants;
import com.richie.component.nats.strategy.NatsIdempotentChecker;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link IdempotentMessageDecorator} 单元测试
 */
class IdempotentMessageDecoratorTest {

    @Test
    void decorate_firstTime_shouldCallInnerHandler() throws Exception {
        NatsIdempotentChecker checker = mock(NatsIdempotentChecker.class);
        when(checker.isFirstTime(anyString(), anyLong())).thenReturn(true);

        AtomicBoolean called = new AtomicBoolean(false);
        NatsMessageHandler inner = msg -> called.set(true);

        IdempotentMessageDecorator decorator = new IdempotentMessageDecorator(checker, 60_000L);
        NatsMessageHandler handler = decorator.decorate(inner);

        Message msg = mockMessage("msg-001");
        handler.handle(msg);

        assertThat(called.get()).isTrue();
    }

    @Test
    void decorate_duplicate_shouldSkipInnerHandler() throws Exception {
        NatsIdempotentChecker checker = mock(NatsIdempotentChecker.class);
        when(checker.isFirstTime(anyString(), anyLong())).thenReturn(false);

        AtomicBoolean called = new AtomicBoolean(false);
        NatsMessageHandler inner = msg -> called.set(true);

        IdempotentMessageDecorator decorator = new IdempotentMessageDecorator(checker, 60_000L);
        NatsMessageHandler handler = decorator.decorate(inner);

        Message msg = mockMessage("msg-001");
        handler.handle(msg);

        assertThat(called.get()).isFalse();
        verify(msg).ack(); // Duplicate should still ack
    }

    @Test
    void decorate_innerThrows_shouldClearIdempotentRecord() throws Exception {
        NatsIdempotentChecker checker = mock(NatsIdempotentChecker.class);
        when(checker.isFirstTime(anyString(), anyLong())).thenReturn(true);

        NatsMessageHandler inner = msg -> { throw new RuntimeException("processing error"); };

        IdempotentMessageDecorator decorator = new IdempotentMessageDecorator(checker, 60_000L);
        NatsMessageHandler handler = decorator.decorate(inner);

        Message msg = mockMessage("msg-001");

        assertThatThrownBy(() -> handler.handle(msg))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("processing error");

        verify(checker).clear(contains(NatsConstants.IDEMPOTENT_KEY_PREFIX));
    }

    @Test
    void decorate_withNoMessageIdHeader_shouldUseFallback() throws Exception {
        NatsIdempotentChecker checker = mock(NatsIdempotentChecker.class);
        when(checker.isFirstTime(anyString(), anyLong())).thenReturn(true);

        AtomicBoolean called = new AtomicBoolean(false);
        NatsMessageHandler inner = msg -> called.set(true);

        IdempotentMessageDecorator decorator = new IdempotentMessageDecorator(checker, 60_000L);
        NatsMessageHandler handler = decorator.decorate(inner);

        // Message without message-id header, not JetStream
        Message msg = mock(Message.class);
        when(msg.getHeaders()).thenReturn(null);
        when(msg.isJetStream()).thenReturn(false);
        when(msg.getSubject()).thenReturn("test.subject");
        when(msg.getData()).thenReturn("hello".getBytes());

        handler.handle(msg);

        assertThat(called.get()).isTrue();
        // Verify checker was called with a fallback key (subject + data hash)
        verify(checker).isFirstTime(contains(NatsConstants.IDEMPOTENT_KEY_PREFIX), eq(60_000L));
    }

    private Message mockMessage(String messageId) {
        Message msg = mock(Message.class);
        Headers headers = new Headers();
        if (messageId != null) {
            headers.put(NatsConstants.HEADER_MESSAGE_ID, messageId);
        }
        when(msg.getHeaders()).thenReturn(headers);
        when(msg.isJetStream()).thenReturn(false);
        when(msg.getSubject()).thenReturn("test.subject");
        when(msg.getData()).thenReturn("data".getBytes());
        return msg;
    }
}
