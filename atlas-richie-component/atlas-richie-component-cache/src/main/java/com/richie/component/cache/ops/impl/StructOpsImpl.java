package com.richie.component.cache.ops.impl;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.HashFunction;
import com.richie.component.cache.ops.L2SyncHelper;
import com.richie.component.cache.ops.StructOps;
import tools.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Component
@RequiredArgsConstructor
public class StructOpsImpl implements StructOps {

    private static final KeyTypeEnum KT = KeyTypeEnum.HASH;

    private final HashFunction fn;
    private final L2SyncHelper l2;

    @Override
    public <T> T get(String key, Class<T> clazz) {
        return l2.get(KT, key, () -> fn.getObjectFromHash(key, clazz));
    }

    @Override
    public <T> T get(String key, TypeReference<T> reference) {
        return l2.get(KT, key, () -> fn.getObjectFromHash(key, reference));
    }

    @Override
    public void set(String key, Object value) {
        fn.addObject(key, value);
        l2.put(KT, key, value);
    }

    @Override
    public void set(String key, Object value, long timeoutMillis) {
        fn.addObject(key, value, timeoutMillis);
        l2.put(KT, key, value, timeoutMillis);
    }

    @Override
    public <T> T refresh(String key, UnaryOperator<T> func) {
        T result = fn.refreshObject(key, func);
        l2.put(KT, key, result);
        return result;
    }

    @Override
    public <T> T getWithLock(String key, Class<T> clazz, long timeoutMillis, Supplier<T> dbLoader) {
        return l2.getWithLock(KT, key,
                () -> fn.getObjectFromHashWithLock(key, clazz, dbLoader, timeoutMillis));
    }

    @Override
    public <T> T getWithLock(String key, TypeReference<T> reference, long timeoutMillis, Supplier<T> dbLoader) {
        return l2.getWithLock(KT, key,
                () -> fn.getObjectFromHashWithLock(key, reference, dbLoader, timeoutMillis));
    }
}
