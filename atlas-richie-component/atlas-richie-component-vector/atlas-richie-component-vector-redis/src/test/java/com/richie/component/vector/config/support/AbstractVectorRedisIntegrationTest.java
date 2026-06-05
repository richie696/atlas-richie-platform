package com.richie.component.vector.config.support;

import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;

import java.util.function.Supplier;

@VectorRedisIntegrationTest
public abstract class AbstractVectorRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return VectorRedisIntegrationTestSupport::getInstance;
    }
}
