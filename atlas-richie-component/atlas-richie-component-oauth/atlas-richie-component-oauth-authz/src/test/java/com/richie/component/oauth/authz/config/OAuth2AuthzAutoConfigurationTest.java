/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.oauth.authz.config;

import com.richie.component.oauth.core.config.OAuth2AutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@link OAuth2AuthzAutoConfiguration} 的注解契约 —— 锁定装配边界，防止后续改动无意识破坏设计意图。
 *
 * <h3>设计意图</h3>
 * 组件明确选择<strong>不使用 {@code @ComponentScan}</strong>，而是通过显式 {@code @Bean} 方法 +
 * {@code @ConditionalOnProperty(enabled=true)} 条件装配。这样在配置 {@code enabled=false} 时，
 * 整个 oauth-authz 包内的任何 {@code @Component} 都不会被加载，避免无关 bean 进入上下文。
 */
class OAuth2AuthzAutoConfigurationTest {

    @Test
    void hasAutoConfigurationAnnotation() {
        assertThat(OAuth2AuthzAutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class))
                .as("@AutoConfiguration 必须存在，以被 Spring Boot 自动装配机制识别")
                .isTrue();
    }

    @Test
    void hasConditionalOnPropertyEnabled() {
        ConditionalOnProperty conditionalOnProperty = OAuth2AuthzAutoConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(conditionalOnProperty)
                .as("@ConditionalOnProperty 必须存在 —— enabled=false 时整个 oauth-authz 模块不装配")
                .isNotNull();
        assertThat(conditionalOnProperty.havingValue())
                .as("@ConditionalOnProperty.havingValue 必须为 true")
                .isEqualTo("true");
        assertThat(conditionalOnProperty.name())
                .as("@ConditionalOnProperty.name 必须包含 enabled")
                .contains("enabled");
    }

    @Test
    void doesNotUseComponentScan() {
        // 故意反向断言：设计契约是不使用 @ComponentScan，依赖显式 @Bean 装配
        assertThat(OAuth2AuthzAutoConfiguration.class.isAnnotationPresent(ComponentScan.class))
                .as("@ComponentScan 不得存在 —— 会绕过 @ConditionalOnProperty 导致 enabled=false 时仍扫描全包")
                .isFalse();
    }

    @Test
    void importsOAuth2AutoConfiguration() {
        Import importAnnotation = OAuth2AuthzAutoConfiguration.class
                .getAnnotation(Import.class);
        assertThat(importAnnotation)
                .as("@Import 必须存在 —— authz 模块依赖 oauth-core 的 ClientRegistry / TokenStore / Properties")
                .isNotNull();
        assertThat(importAnnotation.value())
                .as("@Import 必须显式引入 OAuth2AutoConfiguration.class")
                .containsExactly(OAuth2AutoConfiguration.class);
    }

    @Test
    void definesExplicitBeanMethods() {
        // 所有 Bean 必须通过显式 @Bean 方法暴露 —— 这是与 @ComponentScan 的对应契约
        long beanCount = 0;
        for (Method method : OAuth2AuthzAutoConfiguration.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)) {
                beanCount++;
            }
        }
        assertThat(beanCount)
                .as("必须显式定义 4 个 @Bean 方法：authorizationCodeStore / pkceSupport / authorizationEndpoint / authorizationCodeGrant")
                .isGreaterThanOrEqualTo(4);
    }
}
