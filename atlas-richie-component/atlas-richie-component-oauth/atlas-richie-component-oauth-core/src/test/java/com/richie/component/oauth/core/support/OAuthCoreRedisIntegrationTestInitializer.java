package com.richie.component.oauth.core.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class OAuthCoreRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                OAuthCoreRedisIntegrationTestSupport::integrationTestsEnabled,
                pairs -> OAuthCoreRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
