package com.richie.component.oauth.authz.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class OAuthAuthzRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                OAuthAuthzRedisIntegrationTestSupport::integrationTestsEnabled,
                pairs -> OAuthAuthzRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
