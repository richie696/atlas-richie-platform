package com.richie.component.oauth.dcr.support;

import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.function.Supplier;

@OAuthDcrRedisIntegrationTest
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractOAuthDcrRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return OAuthDcrRedisIntegrationTestSupport::getInstance;
    }
}
