package com.richie.component.storage.core.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = StorageIntegrationTestConfiguration.class)
@EnabledIf("com.richie.component.storage.core.support.StorageIntegrationTestSupport#isEnabled")
public @interface StorageIntegrationTest {
}
