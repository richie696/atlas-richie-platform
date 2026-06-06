package com.richie.component.mongodb.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = MongodbIntegrationTestConfiguration.class)
@ContextConfiguration(initializers = MongodbIntegrationTestInitializer.class)
@EnabledIf("com.richie.component.mongodb.support.MongodbIntegrationTestSupport#isEnabled")
public @interface MongodbIntegrationTest {
}
