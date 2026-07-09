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
package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RedirectConfig}.
 */
@DisplayName("RedirectConfig")
class RedirectConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            RedirectConfig config = new RedirectConfig();
            assertThat(config.getSecurityRedirectUri()).isEqualTo("/");
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setSecurityRedirectUri should update value")
        void setSecurityRedirectUriShouldUpdateValue() {
            RedirectConfig config = new RedirectConfig();
            config.setSecurityRedirectUri("/login");
            assertThat(config.getSecurityRedirectUri()).isEqualTo("/login");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            RedirectConfig config = new RedirectConfig();
            config.setSecurityRedirectUri("/custom");

            assertThat(config.getSecurityRedirectUri()).isEqualTo("/custom");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            RedirectConfig a = new RedirectConfig();
            a.setSecurityRedirectUri("/login");
            RedirectConfig b = new RedirectConfig();
            b.setSecurityRedirectUri("/login");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            RedirectConfig a = new RedirectConfig();
            a.setSecurityRedirectUri("/login");
            RedirectConfig b = new RedirectConfig();
            b.setSecurityRedirectUri("/logout");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("equals is consistent with hashCode")
        void equalsIsConsistentWithHashCode() {
            RedirectConfig config = new RedirectConfig();
            config.setSecurityRedirectUri("/test");

            assertThat(config.equals(config)).isTrue();
            assertThat(config.hashCode()).isEqualTo(config.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field name")
        void toStringShouldContainFieldName() {
            RedirectConfig config = new RedirectConfig();
            String str = config.toString();

            assertThat(str).contains("RedirectConfig");
            assertThat(str).contains("securityRedirectUri");
        }

        @Test
        @DisplayName("toString should contain actual value")
        void toStringShouldContainActualValue() {
            RedirectConfig config = new RedirectConfig();
            config.setSecurityRedirectUri("/custom");
            String str = config.toString();

            assertThat(str).contains("/custom");
        }
    }

    @Nested
    @DisplayName("Lombok @Data")
    class LombokDataTest {

        @Test
        @DisplayName("@Data should generate @Getter/@Setter/@ToString/@EqualsAndHashCode")
        void dataAnnotationShouldGenerateRequiredMethods() {
            RedirectConfig config = new RedirectConfig();
            // Verify getter exists and works
            assertThat(config.getSecurityRedirectUri()).isNotNull();
            // Verify setter works
            config.setSecurityRedirectUri("/test");
            assertThat(config.getSecurityRedirectUri()).isEqualTo("/test");
            // Verify toString works (covered above but verify no exception)
            assertThat(config.toString()).isNotNull();
            // Verify equals/hashCode works (covered above but verify no exception)
            assertThat(config.hashCode()).isNotZero();
        }
    }
}
