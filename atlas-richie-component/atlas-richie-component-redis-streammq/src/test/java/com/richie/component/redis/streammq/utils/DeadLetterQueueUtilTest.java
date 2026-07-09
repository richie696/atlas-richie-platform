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
package com.richie.component.redis.streammq.utils;

import com.richie.component.redis.streammq.StreamMQ;
import com.richie.component.redis.streammq.bean.DeadLetterMessage;
import com.richie.component.redis.streammq.function.StreamFunction;
import com.richie.component.redis.streammq.stream.EventContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.connection.stream.RecordId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterQueueUtilTest {

    @Test
    void deadLetterMessage_of_capturesContextAndError() {
        EventContext ctx = new EventContext("order-events", "order-group", RecordId.of("1000-1"));
        RuntimeException error = new RuntimeException("processing failed");

        DeadLetterMessage message = DeadLetterMessage.of(
                "payload", error, ctx, "TestConsumer");

        assertThat(message.originalMessage()).isEqualTo("payload");
        assertThat(message.originalStreamKey()).isEqualTo("order-events");
        assertThat(message.originalGroup()).isEqualTo("order-group");
        assertThat(message.originalRecordId()).isEqualTo("1000-1");
        assertThat(message.errorMessage()).isEqualTo("processing failed");
        assertThat(message.errorType()).isEqualTo("RuntimeException");
        assertThat(message.sourceConsumer()).isEqualTo("TestConsumer");
        assertThat(message.stackTrace()).contains("RuntimeException");
    }

    @Test
    void deadLetterMessage_ofWithBusinessId_includesOptionalFields() {
        EventContext ctx = new EventContext("payments", "pay-group", RecordId.of("2000-2"));

        DeadLetterMessage message = DeadLetterMessage.of(
                42, new IllegalStateException("bad state"), ctx, "PayConsumer", "order-99", "HIGH");

        assertThat(message.businessId()).isEqualTo("order-99");
        assertThat(message.priority()).isEqualTo("HIGH");
        assertThat(message.originalMessageType()).isEqualTo("java.lang.Integer");
    }

    @Test
    void sendToDeadLetterQueue_globalStrategy_publishesToGlobalDlq() {
        StreamFunction streamFn = mock(StreamFunction.class);
        when(streamFn.publish(eq("dlq:global"), any())).thenReturn("1-0");

        EventContext ctx = new EventContext("orders", "order-group", RecordId.of("1000-1"));
        try (MockedStatic<StreamMQ> streamMq = mockStatic(StreamMQ.class)) {
            streamMq.when(StreamMQ::stream).thenReturn(streamFn);

            DeadLetterQueueUtil.sendToDeadLetterQueue("payload", new RuntimeException("fail"), ctx, DeadLetterQueueUtilTest.class);
        }

        verify(streamFn).publish(eq("dlq:global"), any());
    }

    @Test
    void sendToDeadLetterQueue_byMessageType_usesSimpleClassName() {
        StreamFunction streamFn = mock(StreamFunction.class);
        when(streamFn.publish(eq("dlq:type:Integer"), any())).thenReturn("2-0");

        EventContext ctx = new EventContext("payments", "pay-group", RecordId.of("2000-2"));
        try (MockedStatic<StreamMQ> streamMq = mockStatic(StreamMQ.class)) {
            streamMq.when(StreamMQ::stream).thenReturn(streamFn);

            DeadLetterQueueUtil.sendToDeadLetterQueue(
                    42,
                    new IllegalStateException("bad"),
                    ctx,
                    DeadLetterQueueUtilTest.class,
                    DeadLetterQueueUtil.DeadLetterStrategy.BY_MESSAGE_TYPE);
        }

        verify(streamFn).publish(eq("dlq:type:Integer"), any());
    }

    @Test
    void sendToDeadLetterQueue_bySourceStream_usesOriginalStreamKey() {
        StreamFunction streamFn = mock(StreamFunction.class);
        when(streamFn.publish(eq("dlq:stream:inventory"), any())).thenReturn("3-0");

        EventContext ctx = new EventContext("inventory", "inv-group", RecordId.of("3000-3"));
        try (MockedStatic<StreamMQ> streamMq = mockStatic(StreamMQ.class)) {
            streamMq.when(StreamMQ::stream).thenReturn(streamFn);

            DeadLetterQueueUtil.sendToDeadLetterQueue(
                    "payload",
                    new RuntimeException("fail"),
                    ctx,
                    DeadLetterQueueUtilTest.class,
                    DeadLetterQueueUtil.DeadLetterStrategy.BY_SOURCE_STREAM);
        }

        verify(streamFn).publish(eq("dlq:stream:inventory"), any());
    }

    @Test
    void sendToDeadLetterQueue_hybrid_publishesToAllTargets() {
        StreamFunction streamFn = mock(StreamFunction.class);
        when(streamFn.publish(any(), any())).thenReturn("4-0");

        EventContext ctx = new EventContext("events", "evt-group", RecordId.of("4000-4"));
        try (MockedStatic<StreamMQ> streamMq = mockStatic(StreamMQ.class)) {
            streamMq.when(StreamMQ::stream).thenReturn(streamFn);

            DeadLetterQueueUtil.sendToDeadLetterQueue(
                    "payload",
                    new RuntimeException("fail"),
                    ctx,
                    DeadLetterQueueUtilTest.class,
                    DeadLetterQueueUtil.DeadLetterStrategy.HYBRID);
        }

        verify(streamFn).publish(eq("dlq:global"), any());
        verify(streamFn).publish(eq("dlq:type:String"), any());
        verify(streamFn).publish(eq("dlq:stream:events"), any());
    }

    @Test
    void sendToDeadLetterQueue_whenPublishFails_shouldNotThrow() {
        StreamFunction streamFn = mock(StreamFunction.class);
        when(streamFn.publish(any(), any())).thenThrow(new RuntimeException("redis down"));

        EventContext ctx = new EventContext("orders", "order-group", RecordId.of("1000-1"));
        try (MockedStatic<StreamMQ> streamMq = mockStatic(StreamMQ.class)) {
            streamMq.when(StreamMQ::stream).thenReturn(streamFn);

            DeadLetterQueueUtil.sendToDeadLetterQueue("payload", new RuntimeException("fail"), ctx, DeadLetterQueueUtilTest.class);
        }
    }
}
