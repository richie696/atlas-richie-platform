package com.richie.component.messaging.enums.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class MessagingRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                MessagingRedisIntegrationTestSupport::isEnabled,
                pairs -> MessagingRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
