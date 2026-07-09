/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
