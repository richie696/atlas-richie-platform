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
