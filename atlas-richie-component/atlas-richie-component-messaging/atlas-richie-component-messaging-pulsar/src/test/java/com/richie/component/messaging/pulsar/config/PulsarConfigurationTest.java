/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.messaging.pulsar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PulsarConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PulsarConfiguration.class));

    @Nested
    @DisplayName("Spring Context Loading")
    class ContextLoadingTests {

        @Test
        @DisplayName("should load PulsarConfiguration without errors")
        void shouldLoadPulsarConfiguration() {
            contextRunner.run(context -> assertThat(context).isNotNull());
        }
    }

    @Nested
    @DisplayName("PulsarConfiguration Instance Tests")
    class InstanceTests {

        @Test
        @DisplayName("should create PulsarConfiguration instance directly")
        void shouldCreatePulsarConfigurationInstance() {
            PulsarConfiguration config = new PulsarConfiguration();
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("should have toString method")
        void shouldHaveToString() {
            PulsarConfiguration config = new PulsarConfiguration();
            String result = config.toString();
            assertThat(result).isNotNull();
            assertThat(result).contains("PulsarConfiguration");
        }

        @Test
        @DisplayName("should have hashCode method")
        void shouldHaveHashCode() {
            PulsarConfiguration config = new PulsarConfiguration();
            int hashCode = config.hashCode();
            assertThat(hashCode).isNotZero();
        }

        @Test
        @DisplayName("should have equals method")
        void shouldHaveEquals() {
            PulsarConfiguration config = new PulsarConfiguration();
            assertThat(config.equals(config)).isTrue();
            assertThat(config.equals(null)).isFalse();
            assertThat(config.equals(new Object())).isFalse();
        }

        @Test
        @DisplayName("multiple instances should be independent")
        void multipleInstancesShouldBeIndependent() {
            PulsarConfiguration config1 = new PulsarConfiguration();
            PulsarConfiguration config2 = new PulsarConfiguration();
            assertThat(config1).isNotSameAs(config2);
            assertThat(config1.equals(config2)).isFalse();
            assertThat(config1.hashCode()).isNotEqualTo(config2.hashCode());
        }
    }
}
