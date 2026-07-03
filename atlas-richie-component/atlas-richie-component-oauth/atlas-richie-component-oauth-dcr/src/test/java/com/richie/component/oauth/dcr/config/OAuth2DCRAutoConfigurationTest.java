package com.richie.component.oauth.dcr.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OAuth2DCRAutoConfiguration} 设计契约测试。
 *
 * <p>本类不验证实现细节（如 Bean 的具体依赖注入），而是锁定该自动装配类的设计意图，
 * 防止后续维护中误改方向。设计契约：
 * <ul>
 *   <li>作为 {@code @AutoConfiguration} 入口，由 Spring Boot 自动发现</li>
 *   <li>仅在 {@code platform.component.oauth.enabled=true} 时激活
 *       （{@link ConditionalOnProperty @ConditionalOnProperty} 守门）</li>
 *   <li>所有 Bean 通过显式 {@link Bean @Bean} 方法注册，
 *       不使用 {@link ComponentScan @ComponentScan} 扫描（避免隐式装配）</li>
 *   <li>每个 Bean 都标注 {@link ConditionalOnMissingBean @ConditionalOnMissingBean}，
 *       允许业务方通过自定义 Bean 实现替换默认实现（SPI 扩展点）</li>
 *   <li>通过 {@link Import @Import} 复用 {@code OAuth2AutoConfiguration}</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-06-12
 */
@DisplayName("OAuth2DCRAutoConfiguration 设计契约测试")
class OAuth2DCRAutoConfigurationTest {

    private final Class<OAuth2DCRAutoConfiguration> configClass = OAuth2DCRAutoConfiguration.class;

    // ============================================================================================
    // 类级注解契约
    // ============================================================================================

    @Test
    @DisplayName("类上标注 @AutoConfiguration — 由 Spring Boot 自动发现")
    void hasAutoConfigurationAnnotation() {
        AutoConfiguration annotation = configClass.getAnnotation(AutoConfiguration.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("类上没有 @ComponentScan — 所有 Bean 显式注册")
    void doesNotUseComponentScan() {
        ComponentScan annotation = configClass.getAnnotation(ComponentScan.class);

        assertThat(annotation).isNull();
    }

    @Test
    @DisplayName("类上标注 @ConditionalOnProperty — 仅在 enabled=true 时激活")
    void hasConditionalOnPropertyEnabled() {
        ConditionalOnProperty annotation = configClass.getAnnotation(ConditionalOnProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("platform.component.oauth");
        assertThat(annotation.name()).containsExactly("enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
    }

    @Test
    @DisplayName("类上标注 @Import — 复用 OAuth2AutoConfiguration")
    void hasImportOnOAuth2AutoConfiguration() {
        Import annotation = configClass.getAnnotation(Import.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).hasSize(1);
        assertThat(annotation.value()[0].getName())
                .isEqualTo("com.richie.component.oauth.core.config.OAuth2AutoConfiguration");
    }

    // ============================================================================================
    // Bean 方法契约
    // ============================================================================================

    @Test
    @DisplayName("显式定义 3 个 @Bean 方法 — 无隐式扫描发现")
    void definesExplicitBeanMethods() {
        long beanMethodCount = Arrays.stream(configClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .count();

        assertThat(beanMethodCount).isEqualTo(3L);
    }

    @Test
    @DisplayName("每个 @Bean 方法都标注 @ConditionalOnMissingBean — 允许业务方覆盖")
    void everyBeanMethodAllowsOverride() {
        Method[] beanMethods = Arrays.stream(configClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .toArray(Method[]::new);

        assertThat(beanMethods)
                .as("所有 @Bean 方法都必须标注 @ConditionalOnMissingBean")
                .allSatisfy(m -> assertThat(m.isAnnotationPresent(ConditionalOnMissingBean.class))
                        .as("@Bean 方法 %s 应标注 @ConditionalOnMissingBean", m.getName())
                        .isTrue());
    }

    @Test
    @DisplayName("默认构造函数可以创建实例")
    void defaultConstructor_createsInstance() {
        OAuth2DCRAutoConfiguration configuration = new OAuth2DCRAutoConfiguration();

        assertThat(configuration).isNotNull();
    }
}
