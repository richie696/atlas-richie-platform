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
package com.richie.component.storage.local.config;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.ops.CacheInfrastructure;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.ValueOps;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CacheConfigurationCheckerTest {

    @Test
    void checkCacheConfiguration_whenTypeCachesDisabled_logsWarnings() {
        KeyOps keyOps = mock(KeyOps.class, org.mockito.Mockito.withSettings().extraInterfaces(CacheInfrastructure.class));
        CacheInfrastructure infra = (CacheInfrastructure) keyOps;
        ValueOps valueOps = mock(ValueOps.class);
        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KeyTypeEnum.STRING)).thenReturn(false);
        when(infra.enableKeyTypeCache(KeyTypeEnum.HASH)).thenReturn(false);
        when(valueOps.get(anyString(), eq(String.class))).thenReturn("ok");
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong());
        doNothing().when(keyOps).removeCache(anyString());

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            new CacheConfigurationChecker().checkCacheConfiguration();
        }
    }

    @Test
    void checkCacheConfiguration_whenL2Enabled_runsWithoutException() {
        KeyOps keyOps = mock(KeyOps.class, org.mockito.Mockito.withSettings().extraInterfaces(CacheInfrastructure.class));
        CacheInfrastructure infra = (CacheInfrastructure) keyOps;
        ValueOps valueOps = mock(ValueOps.class);

        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KeyTypeEnum.STRING)).thenReturn(true);
        when(infra.enableKeyTypeCache(KeyTypeEnum.HASH)).thenReturn(true);
        when(valueOps.get(anyString(), eq(String.class))).thenReturn("test_value");
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong());
        doNothing().when(keyOps).removeCache(anyString());

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            new CacheConfigurationChecker().checkCacheConfiguration();
        }
    }

    @Test
    void checkCacheConfiguration_whenL2Disabled_logsWarningPath() {
        KeyOps keyOps = mock(KeyOps.class, org.mockito.Mockito.withSettings().extraInterfaces(CacheInfrastructure.class));
        CacheInfrastructure infra = (CacheInfrastructure) keyOps;
        when(infra.enableL2Caching()).thenReturn(false);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            new CacheConfigurationChecker().checkCacheConfiguration();
        }
    }

    @Test
    void checkCacheConfiguration_whenInfraMissing_skipsGracefully() {
        KeyOps keyOps = mock(KeyOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            new CacheConfigurationChecker().checkCacheConfiguration();
        }
    }

    @Test
    void logCacheStatus_whenInfraPresent_runsWithoutException() {
        KeyOps keyOps = mock(KeyOps.class, org.mockito.Mockito.withSettings().extraInterfaces(CacheInfrastructure.class));
        CacheInfrastructure infra = (CacheInfrastructure) keyOps;
        when(infra.enableL2Caching()).thenReturn(false);
        when(infra.enableKeyTypeCache(KeyTypeEnum.STRING)).thenReturn(false);
        when(infra.enableKeyTypeCache(KeyTypeEnum.HASH)).thenReturn(false);
        when(infra.getConnectionString()).thenReturn("redis://localhost:6379/15");

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            new CacheConfigurationChecker().logCacheStatus();
        }
    }

    @Test
    void logCacheStatus_whenInfraMissing_logsWarning() {
        KeyOps keyOps = mock(KeyOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            new CacheConfigurationChecker().logCacheStatus();
        }
    }
}
