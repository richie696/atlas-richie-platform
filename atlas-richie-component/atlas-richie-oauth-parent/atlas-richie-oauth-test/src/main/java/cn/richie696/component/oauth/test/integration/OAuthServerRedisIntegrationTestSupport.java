package cn.richie696.component.oauth.test.integration;

import cn.richie696.testing.redis.GenericRedisIntegrationTestSupport;
import cn.richie696.testing.redis.RedisIntegrationTestAccess;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

/**
 * OAuth 测试支撑工具（不属于生产运行时）：OAuth Server 集成测试的 Redis 连接策略与属性注入入口。
 *
 * <p>职责链位置：处于组件通用 Redis 集成测试支持
 * （{@link GenericRedisIntegrationTestSupport}）与 OAuth Server 集成测试注解 / 初始化器之间。
 * 它把 Testcontainers 默认镜像（{@code redis:7-alpine}）、OAuth 专用属性追加、
 * 环境变量开关（{@code OAUTH_IT_USE_EXTERNAL=true}）封装在一个单例中，
 * 由 {@link OAuthServerRedisTestInitializer} 与 {@link OAuthServerRedisIntegrationTest}
 * 共同消费。</p>
 *
 * <p>解决以下问题：OAuth Server 集成测试除了需要一份 Redis 实例，
 * 还需要把 {@code platform.component.oauth.*} 测试属性注入 Spring 上下文；
 * 默认外部依赖是 Testcontainers，本地/CI 可通过环境变量切换到外部 Redis，
 * 而无需修改测试代码。该单例集中持有这些策略，保证 OAuth 模块的
 * 集成测试在 CI / 本地有一致表现。</p>
 *
 * @author richie696
 * @since 2026-08-07
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
