package com.richie.component.oauth.core.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 OAuth Core Redis 集成测试：继承 {@link AbstractOAuthCoreRedisIntegrationTest} 的子类
 * 自动获得 Spring 上下文与 Redis 连接策略。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = OAuthCoreIntegrationTestConfiguration.class)
@ContextConfiguration(initializers = OAuthCoreRedisIntegrationTestInitializer.class)
@EnabledIf("com.richie.component.oauth.core.support.OAuthCoreRedisIntegrationTestSupport#integrationTestsEnabled")
public @interface OAuthCoreRedisIntegrationTest {
}
