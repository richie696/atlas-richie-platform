package com.richie.component.mqtt.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class MqttRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        SpringPropertyInitializer.applyIfAvailable(
                MqttRedisIntegrationTestSupport::isEnabled,
                pairs -> MqttRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }
}
