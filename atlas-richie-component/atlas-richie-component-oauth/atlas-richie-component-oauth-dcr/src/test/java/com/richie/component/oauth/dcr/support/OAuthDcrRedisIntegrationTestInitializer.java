package com.richie.component.oauth.dcr.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class OAuthDcrRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                OAuthDcrRedisIntegrationTestSupport::integrationTestsEnabled,
                pairs -> OAuthDcrRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
