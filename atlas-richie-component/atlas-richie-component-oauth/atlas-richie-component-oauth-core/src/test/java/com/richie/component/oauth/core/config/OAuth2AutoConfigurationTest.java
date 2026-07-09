/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.oauth.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 OAuth2AutoConfiguration 的注解契约 —— 锁定装配边界，防止后续改动无意识破坏设计意图。
 *
 * <h3>设计意图</h3>
 * 组件明确选择<strong>不使用 {@code @ComponentScan}</strong>，而是通过显式 {@code @Bean} 方法 +
 * {@code @ConditionalOnProperty(enabled=true)} 条件装配。这样在配置 {@code enabled=false} 时，
 * 整个 oauth-core 包内的任何 {@code @Component} 都不会被加载，避免无关 bean 进入上下文。
 */
class OAuth2AutoConfigurationTest {

    @Test
    void hasAutoConfigurationAnnotation() {
        assertThat(OAuth2AutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class))
                .as("@AutoConfiguration 必须存在，以被 Spring Boot 自动装配机制识别")
                .isTrue();
    }

    @Test
    void doesNotUseComponentScan() {
        // 故意反向断言：设计契约是不使用 @ComponentScan，依赖显式 @Bean 装配
        assertThat(OAuth2AutoConfiguration.class.isAnnotationPresent(ComponentScan.class))
                .as("@ComponentScan 不得存在 —— 会绕过 @ConditionalOnProperty 导致 enabled=false 时仍扫描全包")
                .isFalse();
    }

    @Test
    void hasEnableConfigurationPropertiesAnnotation() {
        var enableConfigProps = OAuth2AutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertThat(enableConfigProps).isNotNull();
        assertThat(enableConfigProps.value()).contains(OAuth2Properties.class);
    }
}
