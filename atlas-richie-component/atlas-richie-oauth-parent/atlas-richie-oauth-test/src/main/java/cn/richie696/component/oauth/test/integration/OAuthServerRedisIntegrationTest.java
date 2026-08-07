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
 * OAuth 测试支撑工具（不属于生产运行时）：OAuth Server Redis 集成测试的标记注解。
 *
 * <p>职责链位置：处于"测试语义标记"层，与具体测试类（{@code @SpringBootTest}）并列。
 * 它把环境检测（{@code OAuthServerRedisIntegrationTestSupport#integrationTestsEnabled}）、
 * Spring 上下文初始化器（{@link OAuthServerRedisTestInitializer}）与串行执行
 * （{@code @Execution(SAME_THREAD)}）组合在同一个注解上，避免服务工程
 * 在每个测试类里重复声明这三个维度。</p>
 *
 * <p>使用方仍需在具体测试类上声明自己的 {@code @SpringBootTest}，因为 AS 的启动类属于服务工程，
 * 本组件不能反向依赖服务。该注解只负责 Redis 属性注入、环境检测和串行执行。</p>
 *
 * <p>解决以下问题：OAuth Server 的集成测试既要跑在 Testcontainers 提供的 Redis 上，
 * 又要兼容使用外部 Redis 的 CI / 本地开发环境；
 * 通过该注解把"环境探测 + 上下文属性注入 + 串行执行"统一封装，
 * 业务测试类只需 {@code @OAuthServerRedisIntegrationTest} 一行即可获得完整接入。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(initializers = OAuthServerRedisTestInitializer.class)
@EnabledIf("cn.richie696.component.oauth.test.integration.OAuthServerRedisIntegrationTestSupport#integrationTestsEnabled")
public @interface OAuthServerRedisIntegrationTest {
}
