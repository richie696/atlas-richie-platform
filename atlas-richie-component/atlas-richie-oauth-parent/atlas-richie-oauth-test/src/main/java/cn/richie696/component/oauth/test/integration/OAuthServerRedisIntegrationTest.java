package cn.richie696.component.oauth.test.integration;

import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 OAuth Server Redis 集成测试。
 *
 * <p>使用方仍需在具体测试类上声明自己的 {@code @SpringBootTest}，因为 AS 的启动类属于服务工程，
 * 本组件不能反向依赖服务。该注解只负责 Redis 属性注入、环境检测和串行执行。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(initializers = OAuthServerRedisTestInitializer.class)
@EnabledIf("cn.richie696.component.oauth.test.integration.OAuthServerRedisIntegrationTestSupport#integrationTestsEnabled")
public @interface OAuthServerRedisIntegrationTest {
}
