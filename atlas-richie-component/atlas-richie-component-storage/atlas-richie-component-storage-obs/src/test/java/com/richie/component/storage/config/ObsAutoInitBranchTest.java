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
package com.richie.component.storage.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ObsAutoInitBranchTest {

    @Test
    void shouldHaveConfigurationAnnotation() {
        assertThat(ObsAutoConfiguration.class.getAnnotation(Configuration.class)).isNotNull();
    }

    @Test
    void providerBeanMethod_shouldBeAnnotatedWithBean() throws NoSuchMethodException {
        Method method = ObsAutoConfiguration.class.getMethod("obsStorageEngineProvider");
        assertThat(method.getAnnotation(Bean.class)).isNotNull();
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("StorageEngineProvider");
    }

    @Test
    void providerBeanMethod_shouldNotHaveConditionalOnProperty() throws NoSuchMethodException {
        Method method = ObsAutoConfiguration.class.getMethod("obsStorageEngineProvider");
        assertThat(method.getAnnotationsByType(ConditionalOnProperty.class)).isEmpty();
    }

    @Test
    void clientBeanMethod_shouldBeConditionalOnEngineHuaweiObs() throws NoSuchMethodException {
        Method method = ObsAutoConfiguration.class.getMethod("obsClient", StorageProperties.class);
        var conditions = Arrays.stream(method.getAnnotationsByType(ConditionalOnProperty.class))
                .filter(c -> "platform.component.storage.object".equals(c.prefix()))
                .filter(c -> Arrays.asList(c.name()).contains("engine"))
                .filter(c -> "huawei_obs".equals(c.havingValue()))
                .findFirst();
        assertThat(conditions).isPresent();
    }

    @Test
    void clientBeanMethod_shouldBeConditionalOnAutoInitTrue() throws NoSuchMethodException {
        Method method = ObsAutoConfiguration.class.getMethod("obsClient", StorageProperties.class);
        var conditions = Arrays.stream(method.getAnnotationsByType(ConditionalOnProperty.class))
                .filter(c -> "platform.component.storage".equals(c.prefix()))
                .filter(c -> Arrays.asList(c.name()).contains("auto-init"))
                .filter(c -> "true".equals(c.havingValue()))
                .findFirst();
        assertThat(conditions).isPresent();
        assertThat(conditions.get().matchIfMissing()).isTrue();
    }
}
