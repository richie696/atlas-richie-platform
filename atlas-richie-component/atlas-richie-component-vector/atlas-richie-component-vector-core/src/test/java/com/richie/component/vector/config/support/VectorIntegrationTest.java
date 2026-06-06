package com.richie.component.vector.config.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = VectorIntegrationTestConfiguration.class)
@EnabledIf("com.richie.component.vector.config.support.VectorIntegrationTestSupport#integrationTestsEnabled")
public @interface VectorIntegrationTest {
}
