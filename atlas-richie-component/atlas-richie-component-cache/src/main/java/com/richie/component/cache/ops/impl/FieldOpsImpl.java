package com.richie.component.cache.ops.impl;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.HashFunction;
import com.richie.component.cache.ops.L2SyncHelper;
import com.richie.component.cache.ops.FieldOps;
import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class FieldOpsImpl implements FieldOps {

    private static final KeyTypeEnum KT = KeyTypeEnum.HASH;

    private final HashFunction fn;
    private final L2SyncHelper l2;

    // ─────────── 单 field ───────────

    @Override
    public void set(String key, String field, Object value) {
        fn.addHash(key, field, value);
    }

    @Override
    public <T> T get(String key, String field, Class<T> clazz) {
        return l2.getWithLock(KT, key + ":" + field,
                () -> fn.getFromHash(key, field, clazz));
    }

    @Override
    public <T> T get(String key, String field, TypeReference<T> reference) {
        return l2.getWithLock(KT, key + ":" + field,
                () -> fn.getFromHash(key, field, reference));
    }

    @Override
    public boolean exists(String key, String field) {
        return fn.existsInHash(key, field);
    }

    // ─────────── 多 field ───────────

    @Override
    public void setAll(String key, Map<String, ?> map, long timeoutMillis) {
        fn.addHash(key, map);
        l2.registerType(key, Map.class);
        l2.put(KT, key, map, timeoutMillis);
    }

    @Override
    public <T> Map<String, T> getAll(String key, Class<T> clazz) {
        return l2.get(KT, key, () -> fn.getAllMapFromHash(key, clazz));
    }

    @Override
    public <T> List<T> get(String key, Collection<String> fields, TypeReference<T> reference) {
        return fn.getFromHash(key, List.copyOf(fields), reference);
    }

    // ─────────── 元信息 ───────────

    @Override
    public Set<String> getFields(String key) {
        return fn.getHashKeyList(key);
    }

    @Override
    public long size(String key) {
        return fn.getHashSize(key);
    }

    @Override
    public void remove(String key, String... fields) {
        fn.removeHashItem(key, fields);
    }

    // ─────────── 批量 ───────────

    @Override
    public void batchSet(Map<String, Map<String, ?>> map) {
        fn.batchAddToHash(map);
    }

    // ─────────── 防击穿 ───────────

    @Override
    public <T> T getWithLock(String key, String field, Class<T> clazz, long timeoutMillis, Supplier<T> dbLoader) {
        String cacheKey = key + ":" + field;
        return l2.getWithLock(KT, cacheKey,
                () -> fn.getFromHashWithLock(key, field, clazz, dbLoader, timeoutMillis));
    }

    @Override
    public <T> T getWithLock(String key, String field, TypeReference<T> reference, long timeoutMillis, Supplier<T> dbLoader) {
        String cacheKey = key + ":" + field;
        return l2.getWithLock(KT, cacheKey,
                () -> fn.getFromHashWithLock(key, field, reference, dbLoader, timeoutMillis));
    }
}
