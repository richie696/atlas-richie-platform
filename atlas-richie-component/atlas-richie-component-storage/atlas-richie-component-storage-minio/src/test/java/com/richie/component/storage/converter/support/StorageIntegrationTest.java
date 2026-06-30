package com.richie.component.storage.converter.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = StorageIntegrationTestConfiguration.class)
@EnabledIf("com.richie.component.storage.converter.support.StorageIntegrationTestSupport#isEnabled")
public @interface StorageIntegrationTest {
}
