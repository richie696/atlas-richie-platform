package com.richie.component.mfa.management.dto.support;

import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;

import java.util.function.Supplier;

@MfaRedisIntegrationTest
public abstract class AbstractMfaRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return MfaRedisIntegrationTestSupport::getInstance;
    }
}
