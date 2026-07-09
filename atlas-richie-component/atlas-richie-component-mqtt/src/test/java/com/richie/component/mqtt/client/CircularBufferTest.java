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
package com.richie.component.mqtt.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CircularBufferTest {

    @Test
    void add_overCapacity_evictsOldest() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);
        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        buffer.add(4);

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.toList()).containsExactly(2, 3, 4);
    }

    @Test
    void clear_resetsBuffer() {
        CircularBuffer<String> buffer = new CircularBuffer<>(2);
        buffer.add("a");
        buffer.clear();

        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.toList()).isEmpty();
    }
}
