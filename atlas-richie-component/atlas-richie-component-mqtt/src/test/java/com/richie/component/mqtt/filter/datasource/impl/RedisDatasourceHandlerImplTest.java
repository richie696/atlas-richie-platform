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
package com.richie.component.mqtt.filter.datasource.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.ValueOps;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.management.openmbean.KeyAlreadyExistsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisDatasourceHandlerImplTest {

    private final RedisDatasourceHandlerImpl handler = new RedisDatasourceHandlerImpl();

    @Test
    void isDuplicate_delegatesToGlobalCache() {
        KeyOps keyOps = mock(KeyOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(keyOps.hasKey(anyString())).thenReturn(true);

            assertThat(handler.isDuplicate("abc")).isTrue();
        }
    }

    @Test
    void saveCache_writesValueWhenNotDuplicate() {
        KeyOps keyOps = mock(KeyOps.class);
        ValueOps valueOps = mock(ValueOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(keyOps.hasKey(anyString())).thenReturn(false);

            handler.saveCache("abc", 1000L);

            verify(valueOps).set(anyString(), eq("1"), eq(1000L));
        }
    }

    @Test
    void saveCache_whenDuplicate_throws() {
        KeyOps keyOps = mock(KeyOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(keyOps.hasKey(anyString())).thenReturn(true);

            assertThatThrownBy(() -> handler.saveCache("abc", 1000L))
                    .isInstanceOf(KeyAlreadyExistsException.class);
        }
    }
}
