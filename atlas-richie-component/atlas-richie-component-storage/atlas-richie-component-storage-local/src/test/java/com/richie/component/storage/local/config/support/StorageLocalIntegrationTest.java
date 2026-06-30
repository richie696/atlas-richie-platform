package com.richie.component.storage.local.config.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = StorageLocalIntegrationTestConfiguration.class)
@ActiveProfiles({"it", "it-local"})
@ContextConfiguration(initializers = StorageLocalIntegrationTestInitializer.class)
@EnabledIf("com.richie.component.storage.local.config.support.StorageLocalIntegrationTestSupport#isEnabled")
public @interface StorageLocalIntegrationTest {
}
