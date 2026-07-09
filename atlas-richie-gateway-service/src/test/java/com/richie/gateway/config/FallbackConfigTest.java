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
package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FallbackConfig} and its nested {@link FallbackConfig.PathMessage}.
 */
@DisplayName("FallbackConfig")
class FallbackConfigTest {

    @Nested
    @DisplayName("FallbackConfig defaults")
    class FallbackConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            FallbackConfig config = new FallbackConfig();
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getDefaultMessage()).isEqualTo("服务暂不可用，请稍后再试！");
            assertThat(config.getPathMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("FallbackConfig setters")
    class FallbackConfigSettersTest {

        @Test
        @DisplayName("setEnabled should update value")
        void setEnabledShouldUpdateValue() {
            FallbackConfig config = new FallbackConfig();
            config.setEnabled(false);
            assertThat(config.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("setDefaultMessage should update value")
        void setDefaultMessageShouldUpdateValue() {
            FallbackConfig config = new FallbackConfig();
            config.setDefaultMessage("Custom default message");
            assertThat(config.getDefaultMessage()).isEqualTo("Custom default message");
        }

        @Test
        @DisplayName("setPathMessages should update value")
        void setPathMessagesShouldUpdateValue() {
            FallbackConfig config = new FallbackConfig();
            FallbackConfig.PathMessage pm = new FallbackConfig.PathMessage();
            pm.setPath("/api/**");
            pm.setMessage("Order service unavailable");
            config.setPathMessages(List.of(pm));

            assertThat(config.getPathMessages()).hasSize(1);
            assertThat(config.getPathMessages().get(0).getPath()).isEqualTo("/api/**");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            FallbackConfig config = new FallbackConfig();
            config.setEnabled(false);
            config.setDefaultMessage("Fallback message");
            FallbackConfig.PathMessage pm = new FallbackConfig.PathMessage();
            pm.setPath("/api/**");
            pm.setMessage("API unavailable");
            config.setPathMessages(List.of(pm));

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getDefaultMessage()).isEqualTo("Fallback message");
            assertThat(config.getPathMessages()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PathMessage defaults")
    class PathMessageDefaultsTest {

        @Test
        @DisplayName("should have null default values")
        void shouldHaveNullDefaultValues() {
            FallbackConfig.PathMessage pm = new FallbackConfig.PathMessage();
            assertThat(pm.getPath()).isNull();
            assertThat(pm.getMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("PathMessage setters")
    class PathMessageSettersTest {

        @Test
        @DisplayName("setPath should update value")
        void setPathShouldUpdateValue() {
            FallbackConfig.PathMessage pm = new FallbackConfig.PathMessage();
            pm.setPath("/api/order/**");
            assertThat(pm.getPath()).isEqualTo("/api/order/**");
        }

        @Test
        @DisplayName("setMessage should update value")
        void setMessageShouldUpdateValue() {
            FallbackConfig.PathMessage pm = new FallbackConfig.PathMessage();
            pm.setMessage("Order service is down");
            assertThat(pm.getMessage()).isEqualTo("Order service is down");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            FallbackConfig.PathMessage pm = new FallbackConfig.PathMessage();
            pm.setPath("/api/**");
            pm.setMessage("Service unavailable");

            assertThat(pm.getPath()).isEqualTo("/api/**");
            assertThat(pm.getMessage()).isEqualTo("Service unavailable");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two FallbackConfig instances with same values should be equal")
        void twoFallbackConfigInstancesWithSameValuesShouldBeEqual() {
            FallbackConfig a = new FallbackConfig();
            a.setEnabled(true);
            a.setDefaultMessage("msg");
            FallbackConfig b = new FallbackConfig();
            b.setEnabled(true);
            b.setDefaultMessage("msg");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two FallbackConfig instances with different values should not be equal")
        void twoFallbackConfigInstancesWithDifferentValuesShouldNotBeEqual() {
            FallbackConfig a = new FallbackConfig();
            a.setEnabled(true);
            FallbackConfig b = new FallbackConfig();
            b.setEnabled(false);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("two PathMessage instances with same values should be equal")
        void twoPathMessageInstancesWithSameValuesShouldBeEqual() {
            FallbackConfig.PathMessage a = new FallbackConfig.PathMessage();
            a.setPath("/api/**");
            a.setMessage("msg");
            FallbackConfig.PathMessage b = new FallbackConfig.PathMessage();
            b.setPath("/api/**");
            b.setMessage("msg");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("FallbackConfig toString should contain field names")
        void fallbackConfigToStringShouldContainFieldNames() {
            FallbackConfig config = new FallbackConfig();
            String str = config.toString();

            assertThat(str).contains("FallbackConfig");
            assertThat(str).contains("enabled");
            assertThat(str).contains("defaultMessage");
        }

        @Test
        @DisplayName("PathMessage toString should contain field names")
        void pathMessageToStringShouldContainFieldNames() {
            FallbackConfig.PathMessage pm = new FallbackConfig.PathMessage();
            String str = pm.toString();

            assertThat(str).contains("PathMessage");
            assertThat(str).contains("path");
            assertThat(str).contains("message");
        }
    }
}
