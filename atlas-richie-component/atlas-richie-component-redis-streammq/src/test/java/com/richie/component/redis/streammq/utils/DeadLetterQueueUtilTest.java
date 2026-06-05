package com.richie.component.redis.streammq.utils;

import com.richie.component.redis.streammq.stream.EventContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterQueueUtilTest {

    @Test
    void deadLetterMessage_of_capturesContextAndError() {
        EventContext ctx = new EventContext("order-events", "order-group", RecordId.of("1000-1"));
        RuntimeException error = new RuntimeException("processing failed");

        DeadLetterQueueUtil.DeadLetterMessage message = DeadLetterQueueUtil.DeadLetterMessage.of(
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

        DeadLetterQueueUtil.DeadLetterMessage message = DeadLetterQueueUtil.DeadLetterMessage.of(
                42, new IllegalStateException("bad state"), ctx, "PayConsumer", "order-99", "HIGH");

        assertThat(message.businessId()).isEqualTo("order-99");
        assertThat(message.priority()).isEqualTo("HIGH");
        assertThat(message.originalMessageType()).isEqualTo("java.lang.Integer");
    }
}
