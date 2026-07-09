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
 * Tests for {@link OAuth2AnomalyDetectionConfig} and its nested classes:
 * {@link OAuth2AnomalyDetectionConfig.TokenReplayConfig},
 * {@link OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig}.
 */
@DisplayName("OAuth2AnomalyDetectionConfig")
class OAuth2AnomalyDetectionConfigTest {

    @Nested
    @DisplayName("OAuth2AnomalyDetectionConfig defaults")
    class OAuth2AnomalyDetectionConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            OAuth2AnomalyDetectionConfig config = new OAuth2AnomalyDetectionConfig();
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getTokenReplay()).isNotNull();
            assertThat(config.getAbnormalRefresh()).isNotNull();
        }
    }

    @Nested
    @DisplayName("OAuth2AnomalyDetectionConfig setters")
    class OAuth2AnomalyDetectionConfigSettersTest {

        @Test
        @DisplayName("setEnabled should update value")
        void setEnabledShouldUpdateValue() {
            OAuth2AnomalyDetectionConfig config = new OAuth2AnomalyDetectionConfig();
            config.setEnabled(false);
            assertThat(config.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("setTokenReplay should update value")
        void setTokenReplayShouldUpdateValue() {
            OAuth2AnomalyDetectionConfig config = new OAuth2AnomalyDetectionConfig();
            OAuth2AnomalyDetectionConfig.TokenReplayConfig tr = new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            tr.setMaxIpsPerToken(5);
            config.setTokenReplay(tr);

            assertThat(config.getTokenReplay().getMaxIpsPerToken()).isEqualTo(5);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            OAuth2AnomalyDetectionConfig config = new OAuth2AnomalyDetectionConfig();
            config.setEnabled(false);
            OAuth2AnomalyDetectionConfig.TokenReplayConfig tr = new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            tr.setMaxIpsPerToken(3);
            config.setTokenReplay(tr);

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getTokenReplay().getMaxIpsPerToken()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("TokenReplayConfig defaults")
    class TokenReplayConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            OAuth2AnomalyDetectionConfig.TokenReplayConfig config =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            assertThat(config.getMaxIpsPerToken()).isEqualTo(2);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("TokenReplayConfig setters")
    class TokenReplayConfigSettersTest {

        @Test
        @DisplayName("setMaxIpsPerToken should update value")
        void setMaxIpsPerTokenShouldUpdateValue() {
            OAuth2AnomalyDetectionConfig.TokenReplayConfig config =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            config.setMaxIpsPerToken(5);
            assertThat(config.getMaxIpsPerToken()).isEqualTo(5);
        }

        @Test
        @DisplayName("setTimeWindowSeconds should update value")
        void setTimeWindowSecondsShouldUpdateValue() {
            OAuth2AnomalyDetectionConfig.TokenReplayConfig config =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            config.setTimeWindowSeconds(120);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            OAuth2AnomalyDetectionConfig.TokenReplayConfig config =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            config.setMaxIpsPerToken(10);
            config.setTimeWindowSeconds(300);

            assertThat(config.getMaxIpsPerToken()).isEqualTo(10);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(300);
        }
    }

    @Nested
    @DisplayName("AbnormalRefreshConfig defaults")
    class AbnormalRefreshConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig config =
                    new OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig();
            assertThat(config.getMaxRefreshesPerMinute()).isEqualTo(10);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("AbnormalRefreshConfig setters")
    class AbnormalRefreshConfigSettersTest {

        @Test
        @DisplayName("setMaxRefreshesPerMinute should update value")
        void setMaxRefreshesPerMinuteShouldUpdateValue() {
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig config =
                    new OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig();
            config.setMaxRefreshesPerMinute(20);
            assertThat(config.getMaxRefreshesPerMinute()).isEqualTo(20);
        }

        @Test
        @DisplayName("setTimeWindowSeconds should update value")
        void setTimeWindowSecondsShouldUpdateValue() {
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig config =
                    new OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig();
            config.setTimeWindowSeconds(120);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig config =
                    new OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig();
            config.setMaxRefreshesPerMinute(15);
            config.setTimeWindowSeconds(300);

            assertThat(config.getMaxRefreshesPerMinute()).isEqualTo(15);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(300);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two TokenReplayConfig instances with same values should be equal")
        void twoTokenReplayConfigInstancesWithSameValuesShouldBeEqual() {
            OAuth2AnomalyDetectionConfig.TokenReplayConfig a =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            a.setMaxIpsPerToken(5);
            OAuth2AnomalyDetectionConfig.TokenReplayConfig b =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            b.setMaxIpsPerToken(5);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two TokenReplayConfig instances with different values should not be equal")
        void twoTokenReplayConfigInstancesWithDifferentValuesShouldNotBeEqual() {
            OAuth2AnomalyDetectionConfig.TokenReplayConfig a =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            a.setMaxIpsPerToken(5);
            OAuth2AnomalyDetectionConfig.TokenReplayConfig b =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            b.setMaxIpsPerToken(10);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("two AbnormalRefreshConfig instances with same values should be equal")
        void twoAbnormalRefreshConfigInstancesWithSameValuesShouldBeEqual() {
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig a =
                    new OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig();
            a.setMaxRefreshesPerMinute(10);
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig b =
                    new OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig();
            b.setMaxRefreshesPerMinute(10);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("TokenReplayConfig toString should contain field names")
        void tokenReplayConfigToStringShouldContainFieldNames() {
            OAuth2AnomalyDetectionConfig.TokenReplayConfig config =
                    new OAuth2AnomalyDetectionConfig.TokenReplayConfig();
            String str = config.toString();

            assertThat(str).contains("TokenReplayConfig");
            assertThat(str).contains("maxIpsPerToken");
            assertThat(str).contains("timeWindowSeconds");
        }

        @Test
        @DisplayName("AbnormalRefreshConfig toString should contain field names")
        void abnormalRefreshConfigToStringShouldContainFieldNames() {
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig config =
                    new OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig();
            String str = config.toString();

            assertThat(str).contains("AbnormalRefreshConfig");
            assertThat(str).contains("maxRefreshesPerMinute");
            assertThat(str).contains("timeWindowSeconds");
        }
    }
}
