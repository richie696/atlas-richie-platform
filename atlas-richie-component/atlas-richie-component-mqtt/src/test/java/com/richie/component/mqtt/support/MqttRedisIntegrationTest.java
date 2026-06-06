package com.richie.component.mqtt.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = MqttRedisIntegrationTestConfiguration.class,
        initializers = MqttRedisIntegrationTestInitializer.class)
@EnabledIf("com.richie.component.mqtt.support.MqttRedisIntegrationTestSupport#isEnabled")
public @interface MqttRedisIntegrationTest {
}
