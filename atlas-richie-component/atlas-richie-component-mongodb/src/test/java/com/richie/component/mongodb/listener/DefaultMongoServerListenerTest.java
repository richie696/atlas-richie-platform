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

import com.mongodb.event.ServerClosedEvent;
import com.mongodb.event.ServerDescriptionChangedEvent;
import com.mongodb.event.ServerOpeningEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DefaultMongoServerListenerTest {

    private final DefaultMongoServerListener listener = new DefaultMongoServerListener();

    @Test
    void callbacks_shouldNotThrow() {
        assertThatCode(() -> listener.serverOpening(mock(ServerOpeningEvent.class)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.serverClosed(mock(ServerClosedEvent.class)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.serverDescriptionChanged(mock(ServerDescriptionChangedEvent.class)))
                .doesNotThrowAnyException();
    }
}
