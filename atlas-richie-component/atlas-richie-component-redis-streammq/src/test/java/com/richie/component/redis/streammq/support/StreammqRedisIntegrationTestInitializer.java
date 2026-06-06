package com.richie.component.redis.streammq.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class StreammqRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                StreammqRedisIntegrationTestSupport::integrationTestsEnabled,
                pairs -> StreammqRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
