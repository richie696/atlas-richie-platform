package com.richie.component.storage.local.config.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = StorageIntegrationTestConfiguration.class)
@ActiveProfiles("it")
@ContextConfiguration(initializers = StorageRedisIntegrationTestInitializer.class)
@EnabledIf("com.richie.component.storage.local.config.support.StorageRedisIntegrationTestSupport#isEnabled")
public @interface StorageRedisIntegrationTest {
}
