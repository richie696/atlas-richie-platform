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
package com.richie.component.web.core.hang;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class HangEventTest {

    @Test
    void of_buildsEventWithDefaults() {
        HangEvent event = HangEvent.of("GET", "/api/v1/users", 5000L, 1000L, "client-1", "trace-1");
        assertThat(event.method()).isEqualTo("GET");
        assertThat(event.path()).isEqualTo("/api/v1/users");
        assertThat(event.elapsedMillis()).isEqualTo(5000L);
        assertThat(event.thresholdMillis()).isEqualTo(1000L);
        assertThat(event.clientKey()).isEqualTo("client-1");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.stackTrace()).isNotEmpty();
    }

    @Test
    void stackOf_returnsStackOfCurrentThread() {
        StackTraceElement[] stack = HangEvent.stackOf(Thread.currentThread());
        assertThat(stack).isNotEmpty();
        assertThat(Arrays.stream(stack).map(StackTraceElement::getClassName)
                .anyMatch(c -> c.contains("HangEventTest"))).isTrue();
    }

    @Test
    void stackOf_capsAt50Frames() {
        StackTraceElement[] stack = HangEvent.stackOf(Thread.currentThread());
        assertThat(stack.length).isLessThanOrEqualTo(50);
    }
}