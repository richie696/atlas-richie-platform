package com.richie.component.mongodb.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class MongodbIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                MongodbIntegrationTestSupport::isEnabled,
                pairs -> MongodbIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
