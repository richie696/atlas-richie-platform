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
package com.richie.component.storage.local.config.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.storage.local.config.support.AbstractStorageRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageCacheOpsIT extends AbstractStorageRedisIntegrationTest {

    @Test
    void fileExistsCache_roundTripThroughGlobalCache() {
        String cacheKey = "it:file:exists:wave-b-demo.txt";
        GlobalCache.value().set(cacheKey, true, 60_000L);
        assertThat(GlobalCache.value().get(cacheKey, Boolean.class)).isTrue();
        GlobalCache.key().removeCache(cacheKey);
        assertThat(GlobalCache.value().get(cacheKey, Boolean.class)).isNull();
    }
}
