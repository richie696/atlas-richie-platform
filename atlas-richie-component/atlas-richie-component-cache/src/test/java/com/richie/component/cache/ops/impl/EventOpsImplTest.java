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
package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.EventFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.MessageListener;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventOpsImplTest {

    @Mock
    private EventFunction fn;

    @Mock
    private MessageListener listener;

    @InjectMocks
    private EventOpsImpl ops;

    @Test
    void subscribeKeyEvent_delegates() {
        ops.subscribeKeyEvent("__keyevent@0__:expired", listener);
        verify(fn).subscribeKeyEvent("__keyevent@0__:expired", listener);
    }
}
