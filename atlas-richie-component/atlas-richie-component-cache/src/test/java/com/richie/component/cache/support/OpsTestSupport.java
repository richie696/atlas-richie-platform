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
package com.richie.component.cache.support;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.ops.L2SyncHelper;
import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import org.mockito.stubbing.Answer;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ops 层单测公共 Mock 辅助。
 */
public final class OpsTestSupport {

    private OpsTestSupport() {
    }

    /** 让 L2 mock 直接执行 Redis loader（模拟 L2 未命中）。 */
    @SuppressWarnings("unchecked")
    public static <T> void passthroughL2Get(L2SyncHelper l2, KeyTypeEnum keyType, String key) {
        when(l2.get(eq(keyType), eq(key), any())).thenAnswer((Answer<T>) invocation -> {
            Supplier<T> loader = invocation.getArgument(2);
            return loader.get();
        });
    }

    @SuppressWarnings("unchecked")
    public static <T> void passthroughL2GetWithLock(L2SyncHelper l2, KeyTypeEnum keyType, String key) {
        when(l2.getWithLock(eq(keyType), eq(key), any())).thenAnswer((Answer<T>) invocation -> {
            Supplier<T> loader = invocation.getArgument(2);
            return loader.get();
        });
    }

    public static AtlasRedisProperties.RedisPerf enabledPerf() {
        AtlasRedisProperties.RedisPerf perf = new AtlasRedisProperties.RedisPerf();
        perf.setEnabled(true);
        perf.setWarnNonO1(true);
        perf.setBlockForbiddenTiers(false);
        perf.setBlockStringPayloadViolations(false);
        perf.setWarnStringPayloadAntiPatterns(true);
        perf.setTocSoftMs(8L);
        perf.setTocHardMs(50L);
        return perf;
    }
}
