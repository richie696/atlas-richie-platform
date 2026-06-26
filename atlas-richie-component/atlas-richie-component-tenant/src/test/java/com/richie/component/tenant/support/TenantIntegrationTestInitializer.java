package com.richie.component.tenant.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class TenantIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                PostgresTestSupport::isEnabled,
                pairs -> PostgresTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
