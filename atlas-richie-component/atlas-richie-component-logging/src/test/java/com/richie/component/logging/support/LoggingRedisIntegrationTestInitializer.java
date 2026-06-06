package com.richie.component.logging.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class LoggingRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                LoggingRedisIntegrationTestSupport::isEnabled,
                pairs -> LoggingRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
