package com.richie.component.storage.local.config.support;

import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;

import java.util.function.Supplier;

@StorageRedisIntegrationTest
public abstract class AbstractStorageRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return StorageRedisIntegrationTestSupport::getInstance;
    }
}
