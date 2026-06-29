package com.richie.component.oauth.core.support;

import com.richie.testing.redis.GenericRedisIntegrationTestSupport;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public final class OAuthCoreRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private static final GenericRedisIntegrationTestSupport DELEGATE = GenericRedisIntegrationTestSupport.create(
            DockerImageName.parse("redis:7-alpine"),
            15,
            "Redis 集成测试需要 Docker（Testcontainers）。请安装并启动 Docker 后执行 mvn verify；"
                    + "CI 请设置 IT_REQUIRE_DOCKER=true。本机已有 Redis 时可设 "
                    + "IT_USE_EXTERNAL=true，参见 atlas-richie-testing-support/README.zh.md",
            OAuthCoreRedisIntegrationTestSupport::appendComponentProperties,
            "OAUTH");

    private OAuthCoreRedisIntegrationTestSupport() {
    }

    private static final OAuthCoreRedisIntegrationTestSupport INSTANCE = new OAuthCoreRedisIntegrationTestSupport();

    public static OAuthCoreRedisIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

    /** JUnit {@code @EnabledIf} 入口（不可与实例 {@link #isEnabled()} 同名）。 */
    public static boolean integrationTestsEnabled() {
        return getInstance().isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return DELEGATE.isEnabled();
    }

    @Override
    public boolean isExternal() {
        return DELEGATE.isExternal();
    }

    @Override
    public void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendPropertyPairs(pairs);
    }

    private static void appendComponentProperties(List<String> pairs) {
        pairs.add("platform.component.oauth.enabled=true");
        pairs.add("platform.component.oauth.token-secret=it-test-secret-key-for-oauth-integration-test-32bytes");
    }
}
