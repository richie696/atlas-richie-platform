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
package com.richie.component.redis.streammq;

import com.richie.component.redis.streammq.function.StreamFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StreamMQTest {

    @AfterEach
    void resetDelegate() throws Exception {
        Field delegateField = StreamMQ.class.getDeclaredField("DELEGATE");
        delegateField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> delegate = (AtomicReference<Object>) delegateField.get(null);
        delegate.set(null);
    }

    @Test
    void streamAndMessaging_shouldExposeInjectedDelegates() throws Exception {
        StreamFunction streamFn = mock(StreamFunction.class);
        StreamMQ.StreamMQDelegate delegate = new StreamMQ.StreamMQDelegate(streamFn);

        StreamMQ facade = newInstance();
        facade.setDelegate(delegate);

        assertThat(StreamMQ.stream()).isSameAs(streamFn);
    }

    @Test
    void setDelegate_shouldOnlyInitializeOnce() throws Exception {
        StreamFunction firstStream = mock(StreamFunction.class);
        StreamFunction secondStream = mock(StreamFunction.class);

        StreamMQ facade = newInstance();
        facade.setDelegate(new StreamMQ.StreamMQDelegate(firstStream));
        facade.setDelegate(new StreamMQ.StreamMQDelegate(secondStream));

        assertThat(StreamMQ.stream()).isSameAs(firstStream);
    }

    private static StreamMQ newInstance() throws Exception {
        var constructor = StreamMQ.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
