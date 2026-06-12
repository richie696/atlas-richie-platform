package com.richie.component.oauth.dcr.support;

import com.richie.testing.redis.GenericRedisIntegrationTestSupport;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public final class OAuthDcrRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private static final GenericRedisIntegrationTestSupport DELEGATE = GenericRedisIntegrationTestSupport.create(
            DockerImageName.parse("redis:7-alpine"),
            15,
            "Redis 集成测试需要 Docker（Testcontainers）。请安装并启动 Docker 后执行 mvn verify；"
                    + "CI 请设置 IT_REQUIRE_DOCKER=true。本机已有 Redis 时可设 "
                    + "IT_USE_EXTERNAL=true，参见 atlas-richie-testing-support/README.md",
            OAuthDcrRedisIntegrationTestSupport::appendComponentProperties,
            "OAUTH");

    private OAuthDcrRedisIntegrationTestSupport() {
    }

    private static final OAuthDcrRedisIntegrationTestSupport INSTANCE = new OAuthDcrRedisIntegrationTestSupport();

    public static OAuthDcrRedisIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

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
        pairs.add("platform.component.oauth.token-secret=it-test-secret-key-for-oauth-dcr-integration-test");
    }
}
