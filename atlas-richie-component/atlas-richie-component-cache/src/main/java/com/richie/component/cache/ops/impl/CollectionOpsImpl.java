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
package com.richie.component.cache.ops.impl;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.SetFunction;
import com.richie.component.cache.ops.CollectionOps;
import com.richie.component.cache.ops.L2SyncHelper;
import com.richie.component.cache.operations.SetCapacityLimits;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionOpsImpl implements CollectionOps {

    private static final KeyTypeEnum KT = KeyTypeEnum.SET;

    private final SetFunction fn;
    private final L2SyncHelper l2;

    @Override
    public <T> Set<T> get(String key, Class<T> clazz) {
        return l2.get(KT, key, () -> fn.getFromSet(key, clazz));
    }

    @Override
    public void set(String key, Set<?> set, long timeoutMillis) {
        fn.addSet(key, set);
        l2.put(KT, key, set, timeoutMillis);
    }

    @Override
    public void add(String key, Object value) {
        long currentSize = fn.getSetSize(key);
        if (SetCapacityLimits.exceedsHardLimit(currentSize)) {
            throw new IllegalStateException(
                    "Set '%s' has reached hard capacity limit (%d elements), refusing add. "
                            .formatted(key, SetCapacityLimits.SET_HARD_MAX_ELEMENTS)
                            + "Please use a more appropriate data structure or shard the data.");
        }
        if (SetCapacityLimits.exceedsRecommended(currentSize)) {
            log.warn("[CacheGuard] Set '{}' size={} exceeds recommended limit ({}), consider sharding or cleanup",
                    key, currentSize, SetCapacityLimits.SET_RECOMMENDED_MAX_ELEMENTS);
        }
        fn.addSetItem(key, value);
        if (l2.isEnabled(KT)) {
            l2.put(KT, key, value);
        }
    }

    @Override
    public long size(String key) {
        return fn.getSetSize(key);
    }

    @Override
    public boolean exists(String key, Object value) {
        return fn.existsInSet(key, value);
    }

    @Override
    public void remove(String key, Object... values) {
        fn.removeSetItem(key, values);
    }

    @Override
    public void batchSet(Map<String, Set<?>> map) {
        fn.batchAddToSet(map);
    }

    @Override
    public <T> T pop(String key, Class<T> clazz) {
        return fn.popDataFromSet(key, clazz);
    }

    @Override
    public <T> Set<T> pop(String key, long count, Class<T> clazz) {
        return fn.popMembersFromSet(key, count, clazz);
    }

    @Override
    public <T> Set<T> getWithLock(String key, Class<T> clazz, long timeoutMillis, Supplier<Set<T>> dbLoader) {
        return l2.getWithLock(KT, key,
                () -> fn.getFromSetWithLock(key, clazz, dbLoader, timeoutMillis));
    }
}
