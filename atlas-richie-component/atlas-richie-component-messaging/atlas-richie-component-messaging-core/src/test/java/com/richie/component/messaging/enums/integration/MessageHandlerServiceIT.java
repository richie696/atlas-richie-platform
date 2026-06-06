package com.richie.component.messaging.enums.integration;

import com.richie.component.messaging.enums.support.AbstractMessagingRedisIntegrationTest;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.handler.impl.MessageHandlerServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class MessageHandlerServiceIT extends AbstractMessagingRedisIntegrationTest {

    @Autowired
    private MessageHandlerServiceImpl messageHandlerService;

    @Test
    void saveCache_routesToRedisHandler() {
        Message<MessageEvent> message = message("handler-topic", "handler-msg-1");

        assertThat(messageHandlerService.saveCache(message, 60_000L)).isTrue();
        assertThat(messageHandlerService.saveCache(message, 60_000L)).isFalse();
        messageHandlerService.clearCache(message);
    }

    private static Message<MessageEvent> message(String topic, String messageId) {
        MessageEvent event = new MessageEvent(topic, "payload");
        event.setMessageId(messageId);
        return MessageBuilder.withPayload(event).build();
    }
}
