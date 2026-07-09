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
package com.richie.component.logging.handler;

import com.richie.component.messaging.event.MessageEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueHandlerImplTest {

    @Mock
    private StreamBridge streamBridge;

    @InjectMocks
    private QueueHandlerImpl queueHandler;

    @Test
    void sendMessage_overloads_delegateToCoreMethod() {
        when(streamBridge.send(anyString(), any(), any(Message.class), any())).thenReturn(true);

        assertThat(queueHandler.sendMessage("t", "body")).isTrue();
        assertThat(queueHandler.sendMessage("t", "binder", (Object) "body")).isTrue();
        assertThat(queueHandler.sendMessage("t", (Object) "body", MimeTypeUtils.APPLICATION_JSON)).isTrue();
        assertThat(queueHandler.sendMessage("t", "binder", (Object) "body", MimeTypeUtils.APPLICATION_JSON)).isTrue();
        assertThat(queueHandler.sendMessage("t", (Object) "body", 100L)).isTrue();
        assertThat(queueHandler.sendMessage("t", "binder", (Object) "body", 100L)).isTrue();
        assertThat(queueHandler.sendMessage("t", (Object) "body", MimeTypeUtils.APPLICATION_JSON, 100L)).isTrue();
        assertThat(queueHandler.sendMessage("t", "binder", "body", MimeTypeUtils.APPLICATION_JSON, 100L)).isTrue();

        verify(streamBridge, org.mockito.Mockito.atLeastOnce()).afterSingletonsInstantiated();
    }

    @Test
    void sendMessage_withDelay_setsHeader() {
        when(streamBridge.send(anyString(), isNull(), any(Message.class), eq(MimeTypeUtils.APPLICATION_JSON)))
                .thenReturn(true);

        boolean sent = queueHandler.sendMessage("access-log-out-0", "payload", 5000L);

        assertThat(sent).isTrue();
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Message<MessageEvent>> captor =
                org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("access-log-out-0"), isNull(), captor.capture(), eq(MimeTypeUtils.APPLICATION_JSON));
        assertThat(captor.getValue().getHeaders().get("x-delay")).isEqualTo(5000L);
        assertThat(captor.getValue().getPayload().isDelay()).isTrue();
    }
}
