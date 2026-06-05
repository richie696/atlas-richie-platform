package com.richie.component.cache.ops;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 无序集合操作接口。
 * <p>对应底层 Set 数据结构，提供集合元素的增删查以及防缓存击穿能力。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface CollectionOps {

    <T> Set<T> get(String key, Class<T> clazz);

    void set(String key, Set<?> set, long timeoutMillis);

    void add(String key, Object value);

    long size(String key);

    boolean exists(String key, Object value);

    void remove(String key, Object... values);

    void batchSet(Map<String, Set<?>> map);

    <T> T pop(String key, Class<T> clazz);

    <T> Set<T> pop(String key, long count, Class<T> clazz);

    <T> Set<T> getWithLock(String key, Class<T> clazz, long timeoutMillis, Supplier<Set<T>> dbLoader);
}
