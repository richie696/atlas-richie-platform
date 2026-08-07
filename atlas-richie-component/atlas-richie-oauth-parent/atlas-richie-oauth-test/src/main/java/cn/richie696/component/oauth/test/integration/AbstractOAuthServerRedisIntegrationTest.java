package cn.richie696.component.oauth.test.integration;

import cn.richie696.testing.redis.AbstractRedisIntegrationTestBase;
import cn.richie696.testing.redis.RedisIntegrationTestAccess;

import java.util.function.Supplier;

/** OAuth Server Redis 集成测试基类，会在每个用例前清理 {@code it:*} 数据。 */
@OAuthServerRedisIntegrationTest
public abstract class AbstractOAuthServerRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return OAuthServerRedisIntegrationTestSupport::getInstance;
    }
}
