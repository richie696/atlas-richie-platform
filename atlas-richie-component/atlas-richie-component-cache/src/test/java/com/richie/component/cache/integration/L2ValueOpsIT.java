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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class L2ValueOpsIT extends AbstractRedisIntegrationTest {

    @DynamicPropertySource
    static void enableL2(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.enable-l2-caching", () -> "true");
        registry.add("spring.data.redis.l2-caching-data[0]", () -> "STRING");
    }

    @Test
    void get_shouldHitRedisAfterSet() {
        GlobalCache.value().set("it:l2:key", "cached", 120_000L);
        assertThat(GlobalCache.value().get("it:l2:key", String.class)).isEqualTo("cached");
    }

    @Test
    void removeCache_shouldClearValue() {
        GlobalCache.value().set("it:l2:del", "x", 120_000L);
        GlobalCache.key().removeCache("it:l2:del");
        assertThat(GlobalCache.value().get("it:l2:del", String.class)).isNull();
    }
}
