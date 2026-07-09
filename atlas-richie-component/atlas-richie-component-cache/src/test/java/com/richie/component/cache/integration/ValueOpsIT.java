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
package com.richie.component.cache.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.richie.component.cache.GlobalCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class ValueOpsIT extends AbstractRedisIntegrationTest {

    @Autowired
    private GlobalCacheManager cacheManager;

    @Test
    void setAndGet_stringValue() {
        GlobalCache.value().set("it:str", "hello", 60_000L);
        assertThat(GlobalCache.value().get("it:str", String.class)).isEqualTo("hello");
    }

    @Test
    void setIfAbsent_shouldRejectDuplicate() {
        assertThat(GlobalCache.value().setIfAbsent("it:once", "a", 60_000L)).isTrue();
        assertThat(GlobalCache.value().setIfAbsent("it:once", "b", 60_000L)).isFalse();
        assertThat(GlobalCache.value().get("it:once", String.class)).isEqualTo("a");
    }

    @Test
    void increment_shouldBeAtomic() {
        GlobalCache.value().set("it:counter", 0, 60_000L);
        assertThat(GlobalCache.value().increment("it:counter", 3L)).isEqualTo(3L);
        assertThat(GlobalCache.value().increment("it:counter", 2L)).isEqualTo(5L);
    }

    @Test
    void globalCacheManager_shouldWireAllOps() {
        assertThat(cacheManager.value()).isNotNull();
        assertThat(cacheManager.queue()).isNotNull();
        assertThat(cacheManager.event()).isNotNull();
    }
}
