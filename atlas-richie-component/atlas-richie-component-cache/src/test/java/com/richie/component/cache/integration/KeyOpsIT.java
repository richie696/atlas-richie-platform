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

class KeyOpsIT extends AbstractRedisIntegrationTest {

    @Test
    void expireAndExists() {
        GlobalCache.value().set("it:key:ttl", "v", 60_000L);
        assertThat(GlobalCache.key().hasKey("it:key:ttl")).isTrue();
        GlobalCache.key().setExpiredTime("it:key:ttl", 120_000L);
        assertThat(GlobalCache.key().getExpire("it:key:ttl")).isPositive();
    }

    @Test
    void removeCache_shouldDeleteKey() {
        GlobalCache.value().set("it:key:del", "v", 60_000L);
        GlobalCache.key().removeCache("it:key:del");
        assertThat(GlobalCache.key().hasKey("it:key:del")).isFalse();
    }
}
