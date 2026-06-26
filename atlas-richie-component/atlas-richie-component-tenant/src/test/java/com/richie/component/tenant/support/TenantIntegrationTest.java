package com.richie.component.tenant.support;

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
@SpringBootTest(classes = TenantIntegrationTestConfiguration.class)
@ContextConfiguration(initializers = TenantIntegrationTestInitializer.class)
@EnabledIf("com.richie.component.tenant.support.PostgresTestSupport#isEnabled")
public @interface TenantIntegrationTest {
}
