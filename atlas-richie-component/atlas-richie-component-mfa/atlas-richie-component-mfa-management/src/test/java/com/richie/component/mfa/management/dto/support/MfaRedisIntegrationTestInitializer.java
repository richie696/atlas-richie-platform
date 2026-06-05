package com.richie.component.mfa.management.dto.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class MfaRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                MfaRedisIntegrationTestSupport::isEnabled,
                pairs -> MfaRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
