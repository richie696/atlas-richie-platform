package com.richie.component.statemachine.storage.support;

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

@StatemachineRedisIntegrationTest
public abstract class AbstractStatemachineRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Autowired
    private GlobalCacheManager cacheManager;

    @Autowired
    private LocalCacheManager localCacheManager;

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return StatemachineRedisIntegrationTestSupport::getInstance;
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
