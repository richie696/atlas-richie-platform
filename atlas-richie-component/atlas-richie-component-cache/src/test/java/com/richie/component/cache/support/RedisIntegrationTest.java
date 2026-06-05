package com.richie.component.cache.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Redis 集成测试：继承 {@link AbstractRedisIntegrationTest} 的子类自动获得 Spring 上下文与 Redis 连接策略。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = CacheIntegrationTestConfiguration.class)
@ContextConfiguration(initializers = RedisIntegrationTestInitializer.class)
@EnabledIf("com.richie.component.cache.support.RedisIntegrationTestSupport#isEnabled")
public @interface RedisIntegrationTest {
}
