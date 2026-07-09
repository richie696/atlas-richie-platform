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
package com.richie.component.redis.streammq.stream;

import com.richie.component.redis.streammq.function.StreamFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventContextTest {

    @AfterEach
    void tearDown() {
        EventContext.setStreamFunction(null);
    }

    @Test
    void ack_delegatesToInjectedStreamFunction() {
        StreamFunction streamFunction = mock(StreamFunction.class);
        EventContext.setStreamFunction(streamFunction);
        RecordId recordId = RecordId.of("1700000000000-0");
        EventContext ctx = new EventContext("orders", "group-a", recordId);

        ctx.ack();

        verify(streamFunction).acknowledge("orders", "group-a", "1700000000000-0");
    }

    @Test
    void ack_throwsWhenStreamFunctionUnavailable() {
        EventContext ctx = new EventContext("orders", "group-a", RecordId.of("1-0"));

        assertThatThrownBy(ctx::ack)
                .isInstanceOfAny(IllegalStateException.class, NullPointerException.class);
    }

}
