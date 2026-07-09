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
package com.richie.component.storage.local.config;

import com.richie.component.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageAutoInitBranchTest {

    @Test
    void providerBeanMethod_shouldBeAnnotatedWithBean() throws NoSuchMethodException {
        Method method = LocalStorageAutoConfiguration.class.getMethod("localStorageEngineProvider");
        assertThat(method.getAnnotation(Bean.class)).isNotNull();
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("StorageEngineProvider");
    }

    @Test
    void providerBeanMethod_shouldNotHaveConditionalOnProperty() throws NoSuchMethodException {
        Method method = LocalStorageAutoConfiguration.class.getMethod("localStorageEngineProvider");
        assertThat(method.getAnnotationsByType(ConditionalOnProperty.class)).isEmpty();
    }

    @Test
    void shouldHaveAutoConfigurationAnnotation() {
        assertThat(LocalStorageAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();
    }

    @Test
    void localStorageAutoConfiguration_hasNoClientBeanMethod() {
        // 本地存储无独立客户端 Bean,仅依赖文件系统
        var clientBeanMethods = Arrays.stream(LocalStorageAutoConfiguration.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .filter(m -> m.getAnnotation(ConditionalOnProperty.class) != null)
                .toList();
        assertThat(clientBeanMethods).isEmpty();
    }
}
