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
package com.richie.component.cache.ops;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.enums.L2CachingRegion;
import com.richie.component.cache.local.manage.LocalCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class L2SyncHelperTest {

    private static final KeyTypeEnum KT = KeyTypeEnum.STRING;

    @Mock
    private CacheInfrastructure infra;

    @InjectMocks
    private L2SyncHelper helper;

    @Test
    void isEnabled_requiresBothFlags() {
        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KT)).thenReturn(true);

        assertThat(helper.isEnabled(KT)).isTrue();
    }

    @Test
    void put_whenDisabled_skipsLocalCache() {
        when(infra.enableL2Caching()).thenReturn(false);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            helper.put(KT, "k", "v");
            local.verifyNoInteractions();
        }
    }

    @Test
    void put_whenEnabled_writesLocalCache() {
        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KT)).thenReturn(true);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            helper.put(KT, "k", "v");
            local.verify(() -> LocalCache.put(L2CachingRegion.GLOBAL_CACHE, "k", "v"));
        }
    }

    @Test
    void put_withTtl_setsExpiry() {
        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KT)).thenReturn(true);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            helper.put(KT, "k", "v", 5000L);
            local.verify(() -> LocalCache.put(L2CachingRegion.GLOBAL_CACHE, "k", "v"));
            local.verify(() -> LocalCache.expiry(L2CachingRegion.GLOBAL_CACHE, "k", 5000L, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void get_whenCacheHit_returnsCached() {
        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KT)).thenReturn(true);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            local.when(() -> LocalCache.get(L2CachingRegion.GLOBAL_CACHE, "k")).thenReturn("cached");

            assertThat(helper.get(KT, "k", () -> "redis")).isEqualTo("cached");
        }
    }

    @Test
    void get_whenMiss_loadsAndCaches() {
        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KT)).thenReturn(true);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            local.when(() -> LocalCache.get(L2CachingRegion.GLOBAL_CACHE, "k")).thenReturn(null);

            assertThat(helper.get(KT, "k", () -> "redis")).isEqualTo("redis");
            local.verify(() -> LocalCache.put(L2CachingRegion.GLOBAL_CACHE, "k", "redis"));
        }
    }

    @Test
    void get_whenL2Disabled_callsLoaderDirectly() {
        when(infra.enableL2Caching()).thenReturn(false);

        assertThat(helper.get(KT, "k", () -> "from-redis")).isEqualTo("from-redis");
    }

    @Test
    void getWithLock_whenCacheHit_returnsCached() {
        when(infra.enableL2Caching()).thenReturn(true);
        when(infra.enableKeyTypeCache(KT)).thenReturn(true);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            local.when(() -> LocalCache.get(L2CachingRegion.GLOBAL_CACHE, "k")).thenReturn("cached");

            assertThat(helper.getWithLock(KT, "k", () -> "redis")).isEqualTo("cached");
        }
    }

    @Test
    void remove_whenL2Enabled_removesFromLocal() {
        when(infra.enableL2Caching()).thenReturn(true);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            helper.remove("k");
            local.verify(() -> LocalCache.remove(L2CachingRegion.GLOBAL_CACHE, "k"));
        }
    }

    @Test
    void removeAll_whenL2Enabled_removesEachKey() {
        when(infra.enableL2Caching()).thenReturn(true);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            helper.removeAll(List.of("a", "b"));
            local.verify(() -> LocalCache.remove(L2CachingRegion.GLOBAL_CACHE, "a"));
            local.verify(() -> LocalCache.remove(L2CachingRegion.GLOBAL_CACHE, "b"));
        }
    }

    @Test
    void registerType_delegatesToInfra() {
        helper.registerType("k", String.class);
        verify(infra).registerType("k", String.class);
    }

    @Test
    void remove_whenL2Disabled_skipsLocal() {
        when(infra.enableL2Caching()).thenReturn(false);

        try (MockedStatic<LocalCache> local = mockStatic(LocalCache.class)) {
            helper.remove("k");
            local.verifyNoInteractions();
        }

        verify(infra, never()).enableKeyTypeCache(KT);
    }
}
