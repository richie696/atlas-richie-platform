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
