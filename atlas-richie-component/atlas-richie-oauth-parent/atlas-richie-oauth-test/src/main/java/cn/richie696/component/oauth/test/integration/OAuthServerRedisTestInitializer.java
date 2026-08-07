package cn.richie696.component.oauth.test.integration;

import cn.richie696.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * OAuth 测试支撑工具（不属于生产运行时）：把 Redis / Testcontainers 与 OAuth 测试属性
 * 注入 OAuth Service（AS）的 Spring 上下文的初始化器。
 *
 * <p>职责链位置：处于 {@link OAuthServerRedisIntegrationTest} 注解与 Spring
 * {@code ApplicationContext} 之间，作为 {@code @ContextConfiguration(initializers = ...)}
 * 的实现。它在上下文准备阶段调用 {@link OAuthServerRedisIntegrationTestSupport}，
 * 探测环境是否启用、收集 Redis 连接属性与 OAuth 专用属性，
 * 并通过 {@link SpringPropertyInitializer} 安全地把它们合并进 AS 的 Environment。</p>
 *
 * <p>解决以下问题：OAuth Server 启动类属于服务工程，组件不能反向依赖；
 * 通过该初始化器把"启动 AS 时需要追加哪些 Redis / OAuth 属性"封装在组件侧，
 * 使服务工程只需在测试类上加一个注解，就能拿到完整的 OAuth + Redis 测试上下文。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OAuthServerRedisTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                OAuthServerRedisIntegrationTestSupport::integrationTestsEnabled,
                pairs -> OAuthServerRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
