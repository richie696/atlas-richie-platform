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
package com.richie.component.redis.streammq.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.cache.local.manage.LocalCacheManager;
import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@StreammqRedisIntegrationTest
public abstract class AbstractStreammqRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Autowired
    private GlobalCacheManager cacheManager;

    @Autowired
    private LocalCacheManager localCacheManager;

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return StreammqRedisIntegrationTestSupport::getInstance;
    }

    @Override
    protected void onRedisIntegrationTestPrepared() {
        forceStaticDelegate(GlobalCache.class, "DELEGATE", cacheManager);
        forceStaticDelegate(LocalCache.class, "MANAGE", localCacheManager);
    }

    private static <T> void forceStaticDelegate(Class<?> holder, String fieldName, T value) {
        try {
            Field field = holder.getDeclaredField(fieldName);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<T> ref = (AtomicReference<T>) field.get(null);
            ref.set(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to wire " + holder.getSimpleName() + " for IT", e);
        }
    }
}
