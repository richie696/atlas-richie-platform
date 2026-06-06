package com.richie.component.messaging.filter.datasource.impl;

import com.richie.component.messaging.event.MessageEvent;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDatasourceHandlerImplTest {

    private final RedisDatasourceHandlerImpl handler = new RedisDatasourceHandlerImpl();

    @Test
    void getCacheKey_usesTopicAndMessageId() {
        Message<MessageEvent> message = message("orders", "msg-42");
        assertThat(handler.getCacheKey(message))
                .isEqualTo("atlas:platform:duplicate:messaging:orders:msg-42");
    }

    private static Message<MessageEvent> message(String topic, String messageId) {
        MessageEvent event = new MessageEvent(topic, "payload");
        event.setMessageId(messageId);
        return MessageBuilder.withPayload(event).build();
    }
}
