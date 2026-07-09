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
package com.richie.component.cache.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedStackIT extends AbstractRedisIntegrationTest {

    record Event(String name) {
    }

    @Test
    void pushPop_lifoOrder() {
        var stack = GlobalCache.stack().create("it:stack:lifo", 10, Event.class);
        stack.push(new Event("first"));
        stack.push(new Event("second"));
        assertThat(stack.pop().name()).isEqualTo("second");
        assertThat(stack.pop().name()).isEqualTo("first");
        GlobalCache.stack().destroy("it:stack:lifo");
    }

    @Test
    void push_shouldRejectWhenFull() {
        var stack = GlobalCache.stack().create("it:stack:full", 2, Event.class);
        assertThat(stack.push(new Event("a"))).isTrue();
        assertThat(stack.push(new Event("b"))).isTrue();
        assertThat(stack.push(new Event("overflow"))).isFalse();
        assertThat(stack.size()).isEqualTo(2L);
        GlobalCache.stack().destroy("it:stack:full");
    }
}
