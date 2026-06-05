package com.richie.component.storage.local.config.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class StorageRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                StorageRedisIntegrationTestSupport::isEnabled,
                pairs -> StorageRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
