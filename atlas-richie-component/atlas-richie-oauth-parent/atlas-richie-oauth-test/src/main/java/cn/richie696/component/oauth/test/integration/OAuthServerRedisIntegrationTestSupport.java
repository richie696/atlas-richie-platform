package cn.richie696.component.oauth.test.integration;

import cn.richie696.testing.redis.GenericRedisIntegrationTestSupport;
import cn.richie696.testing.redis.RedisIntegrationTestAccess;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

/**
 * OAuth Server 集成测试的 Redis 连接策略。
 * 默认使用 Testcontainers；需要复用本机/CI Redis 时设置 {@code OAUTH_IT_USE_EXTERNAL=true}。
 */
public final class OAuthServerRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private static final GenericRedisIntegrationTestSupport DELEGATE =
            GenericRedisIntegrationTestSupport.create(
                    DockerImageName.parse("redis:7-alpine"),
                    15,
                    "OAuth Server Redis 集成测试需要 Docker（Testcontainers）。"
                            + "请启动 Docker，或设置 OAUTH_IT_USE_EXTERNAL=true 使用外部 Redis。",
                    OAuthServerRedisIntegrationTestSupport::appendOAuthProperties,
                    "OAUTH");

    private static final OAuthServerRedisIntegrationTestSupport INSTANCE =
            new OAuthServerRedisIntegrationTestSupport();

    private OAuthServerRedisIntegrationTestSupport() {
    }

    public static OAuthServerRedisIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

    public static boolean integrationTestsEnabled() {
        return INSTANCE.isEnabled();
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

    private static void appendOAuthProperties(List<String> pairs) {
        pairs.add("platform.component.oauth.enabled=true");
        pairs.add("platform.component.oauth.token-secret=it-test-secret-key-for-oauth-server-32bytes");
        pairs.add("platform.component.oauth.issuer=http://localhost/it-oauth");
        pairs.add("platform.component.oauth.audience=it-resource-server");
        pairs.add("platform.component.oauth.enable-daily-issue-limit=false");
        pairs.add("platform.component.oauth.revoke-previous-tokens-on-issue=false");
    }
}
