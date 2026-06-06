package com.richie.component.statemachine.storage.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class StatemachineRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                StatemachineRedisIntegrationTestSupport::integrationTestsEnabled,
                pairs -> StatemachineRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
