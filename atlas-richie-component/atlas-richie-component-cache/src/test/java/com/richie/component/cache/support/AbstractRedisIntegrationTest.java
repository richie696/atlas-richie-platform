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
package com.richie.component.cache.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.cache.local.manage.LocalCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RedisIntegrationTest
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractRedisIntegrationTest {

    private static final String IT_KEY_PATTERN = "it:*";

    @Autowired
    private GlobalCacheManager cacheManager;

    @Autowired
    private LocalCacheManager localCacheManager;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void prepareTestContext() {
        forceStaticDelegate(GlobalCache.class, "DELEGATE", cacheManager);
        forceStaticDelegate(LocalCache.class, "MANAGE", localCacheManager);
        if (RedisIntegrationTestSupport.getInstance().isExternal()) {
            deleteByScan(IT_KEY_PATTERN);
        } else {
            var connection = stringRedisTemplate.getConnectionFactory().getConnection();
            try {
                connection.serverCommands().flushDb();
            } finally {
                connection.close();
            }
        }
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

    private void deleteByScan(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        List<String> batch = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 100) {
                    stringRedisTemplate.delete(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            stringRedisTemplate.delete(batch);
        }
    }
}
