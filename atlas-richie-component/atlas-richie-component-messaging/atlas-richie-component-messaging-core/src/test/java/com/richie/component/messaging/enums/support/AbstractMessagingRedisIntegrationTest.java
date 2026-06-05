package com.richie.component.messaging.enums.support;

import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;

import java.util.function.Supplier;

@MessagingRedisIntegrationTest
public abstract class AbstractMessagingRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return MessagingRedisIntegrationTestSupport::getInstance;
    }
}
