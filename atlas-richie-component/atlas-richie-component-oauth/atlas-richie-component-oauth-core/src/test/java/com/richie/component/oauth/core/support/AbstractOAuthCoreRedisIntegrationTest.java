package com.richie.component.oauth.core.support;

import com.richie.testing.redis.AbstractRedisIntegrationTestBase;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.function.Supplier;

@OAuthCoreRedisIntegrationTest
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractOAuthCoreRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return OAuthCoreRedisIntegrationTestSupport::getInstance;
    }
}
