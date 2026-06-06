package com.richie.component.search.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class SearchIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                SearchIntegrationTestSupport::isEnabled,
                pairs -> SearchIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
