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
package cn.richie696.component.cache.integration;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldOpsIT extends AbstractRedisIntegrationTest {

    @Test
    void increment_shouldAtomicallyUpdateIntegerAndFloatingPointFields() {
        String key = "it:hash-counter";

        assertThat(GlobalCache.field().increment(key, "requests")).isEqualTo(1L);
        assertThat(GlobalCache.field().increment(key, "requests", 4L)).isEqualTo(5L);
        assertThat(GlobalCache.field().decrement(key, "requests", 2L)).isEqualTo(3L);
        assertThat(GlobalCache.field().increment(key, "token-cost", 1.25D)).isEqualTo(1.25D);
        assertThat(GlobalCache.field().increment(key, "token-cost", 0.75D)).isEqualTo(2.0D);
    }
}
