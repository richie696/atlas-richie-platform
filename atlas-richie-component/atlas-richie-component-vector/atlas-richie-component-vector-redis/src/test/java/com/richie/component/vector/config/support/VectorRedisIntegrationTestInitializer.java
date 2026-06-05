package com.richie.component.vector.config.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class VectorRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                VectorRedisIntegrationTestSupport::isEnabled,
                pairs -> VectorRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
