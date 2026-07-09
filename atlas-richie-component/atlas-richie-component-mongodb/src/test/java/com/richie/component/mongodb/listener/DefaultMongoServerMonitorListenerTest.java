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
package com.richie.component.mongodb.listener;

import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatStartedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMongoServerMonitorListenerTest {

    private final DefaultMongoServerMonitorListener listener = new DefaultMongoServerMonitorListener();

    @Test
    void heartbeatCallbacks_shouldNotThrow() {
        assertThatCode(() -> listener.serverHearbeatStarted(mock(ServerHeartbeatStartedEvent.class)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.serverHeartbeatSucceeded(mock(ServerHeartbeatSucceededEvent.class)))
                .doesNotThrowAnyException();
        ServerHeartbeatFailedEvent failed = mock(ServerHeartbeatFailedEvent.class);
        when(failed.getThrowable()).thenReturn(new RuntimeException("heartbeat"));
        assertThatCode(() -> listener.serverHeartbeatFailed(failed))
                .doesNotThrowAnyException();
    }
}
