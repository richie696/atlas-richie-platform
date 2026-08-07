package cn.richie696.component.oauth.test.integration;

import cn.richie696.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** 将 Redis/Testcontainers 和 OAuth 测试属性注入 AS 的 Spring 上下文。 */
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
