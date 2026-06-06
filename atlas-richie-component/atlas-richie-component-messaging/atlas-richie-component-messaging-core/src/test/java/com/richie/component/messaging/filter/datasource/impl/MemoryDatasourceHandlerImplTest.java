package com.richie.component.messaging.filter.datasource.impl;

import com.richie.component.messaging.event.MessageEvent;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryDatasourceHandlerImplTest {

    private final MemoryDatasourceHandlerImpl handler = new MemoryDatasourceHandlerImpl();

    @Test
    void saveCache_firstWriteSucceeds_secondIsDuplicate() {
        Message<MessageEvent> message = message("topic-a", "msg-1");

        assertThat(handler.saveCache(message, 60_000L)).isTrue();
        assertThat(handler.isDuplicate(message)).isTrue();
        assertThat(handler.saveCache(message, 60_000L)).isFalse();

        handler.clearCache(message);
    }

    @Test
    void clearCache_removesEntry() {
        Message<MessageEvent> message = message("topic-b", "msg-2");
        handler.saveCache(message, 60_000L);

        handler.clearCache(message);

        assertThat(handler.isDuplicate(message)).isFalse();
    }

    @Test
    void clearTimeout_removesExpiredEntries() {
        Message<MessageEvent> message = message("topic-c", "msg-3");
        handler.saveCache(message, 1L);
        handler.clearCache(message);
        handler.saveCache(message, 1L);
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        handler.clearTimeout();

        assertThat(handler.isDuplicate(message)).isFalse();
    }

    private static Message<MessageEvent> message(String topic, String messageId) {
        MessageEvent event = new MessageEvent(topic, "payload");
        event.setMessageId(messageId);
        return MessageBuilder.withPayload(event).build();
    }
}
