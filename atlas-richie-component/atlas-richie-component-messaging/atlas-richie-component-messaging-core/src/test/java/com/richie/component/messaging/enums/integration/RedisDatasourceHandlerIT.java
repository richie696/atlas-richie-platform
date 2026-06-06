package com.richie.component.messaging.enums.integration;

import com.richie.component.messaging.enums.support.AbstractMessagingRedisIntegrationTest;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.datasource.impl.RedisDatasourceHandlerImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDatasourceHandlerIT extends AbstractMessagingRedisIntegrationTest {

    @Autowired
    private RedisDatasourceHandlerImpl redisHandler;

    @Test
    void saveCache_usesSetNxForIdempotency() {
        Message<MessageEvent> message = message("it-topic", "it-msg-1");

        assertThat(redisHandler.saveCache(message, 60_000L)).isTrue();
        assertThat(redisHandler.isDuplicate(message)).isTrue();
        assertThat(redisHandler.saveCache(message, 60_000L)).isFalse();

        redisHandler.clearCache(message);
        assertThat(redisHandler.isDuplicate(message)).isFalse();
    }

    private static Message<MessageEvent> message(String topic, String messageId) {
        MessageEvent event = new MessageEvent(topic, "payload");
        event.setMessageId(messageId);
        return MessageBuilder.withPayload(event).build();
    }
}
