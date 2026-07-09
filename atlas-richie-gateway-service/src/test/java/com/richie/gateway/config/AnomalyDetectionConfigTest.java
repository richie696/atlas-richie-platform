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
package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnomalyDetectionConfig} and its nested classes:
 * {@link AnomalyDetectionConfig.BruteForceConfig},
 * {@link AnomalyDetectionConfig.AbnormalIpConfig},
 * {@link AnomalyDetectionConfig.RateLimitConfig}.
 */
@DisplayName("AnomalyDetectionConfig")
class AnomalyDetectionConfigTest {

    @Nested
    @DisplayName("AnomalyDetectionConfig defaults")
    class AnomalyDetectionConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            AnomalyDetectionConfig config = new AnomalyDetectionConfig();
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getBruteForce()).isNotNull();
            assertThat(config.getAbnormalIp()).isNotNull();
            assertThat(config.getRateLimit()).isNotNull();
        }
    }

    @Nested
    @DisplayName("AnomalyDetectionConfig setters")
    class AnomalyDetectionConfigSettersTest {

        @Test
        @DisplayName("setEnabled should update value")
        void setEnabledShouldUpdateValue() {
            AnomalyDetectionConfig config = new AnomalyDetectionConfig();
            config.setEnabled(false);
            assertThat(config.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("setBruteForce should update value")
        void setBruteForceShouldUpdateValue() {
            AnomalyDetectionConfig config = new AnomalyDetectionConfig();
            AnomalyDetectionConfig.BruteForceConfig bf = new AnomalyDetectionConfig.BruteForceConfig();
            bf.setMaxFailuresPerUser(20);
            config.setBruteForce(bf);

            assertThat(config.getBruteForce().getMaxFailuresPerUser()).isEqualTo(20);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            AnomalyDetectionConfig config = new AnomalyDetectionConfig();
            config.setEnabled(false);
            AnomalyDetectionConfig.BruteForceConfig bf = new AnomalyDetectionConfig.BruteForceConfig();
            bf.setMaxFailuresPerUser(50);
            config.setBruteForce(bf);

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getBruteForce().getMaxFailuresPerUser()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("BruteForceConfig defaults")
    class BruteForceConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            AnomalyDetectionConfig.BruteForceConfig config = new AnomalyDetectionConfig.BruteForceConfig();
            assertThat(config.getMaxFailuresPerUser()).isEqualTo(10);
            assertThat(config.getMaxFailuresPerIp()).isEqualTo(20);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(300);
            assertThat(config.getBanDurationSeconds()).isEqualTo(900);
        }
    }

    @Nested
    @DisplayName("BruteForceConfig setters")
    class BruteForceConfigSettersTest {

        @Test
        @DisplayName("setMaxFailuresPerUser should update value")
        void setMaxFailuresPerUserShouldUpdateValue() {
            AnomalyDetectionConfig.BruteForceConfig config = new AnomalyDetectionConfig.BruteForceConfig();
            config.setMaxFailuresPerUser(15);
            assertThat(config.getMaxFailuresPerUser()).isEqualTo(15);
        }

        @Test
        @DisplayName("setMaxFailuresPerIp should update value")
        void setMaxFailuresPerIpShouldUpdateValue() {
            AnomalyDetectionConfig.BruteForceConfig config = new AnomalyDetectionConfig.BruteForceConfig();
            config.setMaxFailuresPerIp(30);
            assertThat(config.getMaxFailuresPerIp()).isEqualTo(30);
        }

        @Test
        @DisplayName("setTimeWindowSeconds should update value")
        void setTimeWindowSecondsShouldUpdateValue() {
            AnomalyDetectionConfig.BruteForceConfig config = new AnomalyDetectionConfig.BruteForceConfig();
            config.setTimeWindowSeconds(600);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(600);
        }

        @Test
        @DisplayName("setBanDurationSeconds should update value")
        void setBanDurationSecondsShouldUpdateValue() {
            AnomalyDetectionConfig.BruteForceConfig config = new AnomalyDetectionConfig.BruteForceConfig();
            config.setBanDurationSeconds(1800);
            assertThat(config.getBanDurationSeconds()).isEqualTo(1800);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            AnomalyDetectionConfig.BruteForceConfig config = new AnomalyDetectionConfig.BruteForceConfig();
            config.setMaxFailuresPerUser(25);
            config.setMaxFailuresPerIp(50);
            config.setTimeWindowSeconds(120);
            config.setBanDurationSeconds(3600);

            assertThat(config.getMaxFailuresPerUser()).isEqualTo(25);
            assertThat(config.getMaxFailuresPerIp()).isEqualTo(50);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(120);
            assertThat(config.getBanDurationSeconds()).isEqualTo(3600);
        }
    }

    @Nested
    @DisplayName("AbnormalIpConfig defaults")
    class AbnormalIpConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            AnomalyDetectionConfig.AbnormalIpConfig config = new AnomalyDetectionConfig.AbnormalIpConfig();
            assertThat(config.getMaxIpChangesPer10Min()).isEqualTo(5);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(600);
        }
    }

    @Nested
    @DisplayName("AbnormalIpConfig setters")
    class AbnormalIpConfigSettersTest {

        @Test
        @DisplayName("setMaxIpChangesPer10Min should update value")
        void setMaxIpChangesPer10MinShouldUpdateValue() {
            AnomalyDetectionConfig.AbnormalIpConfig config = new AnomalyDetectionConfig.AbnormalIpConfig();
            config.setMaxIpChangesPer10Min(10);
            assertThat(config.getMaxIpChangesPer10Min()).isEqualTo(10);
        }

        @Test
        @DisplayName("setTimeWindowSeconds should update value")
        void setTimeWindowSecondsShouldUpdateValue() {
            AnomalyDetectionConfig.AbnormalIpConfig config = new AnomalyDetectionConfig.AbnormalIpConfig();
            config.setTimeWindowSeconds(300);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            AnomalyDetectionConfig.AbnormalIpConfig config = new AnomalyDetectionConfig.AbnormalIpConfig();
            config.setMaxIpChangesPer10Min(8);
            config.setTimeWindowSeconds(180);

            assertThat(config.getMaxIpChangesPer10Min()).isEqualTo(8);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(180);
        }
    }

    @Nested
    @DisplayName("RateLimitConfig defaults")
    class RateLimitConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            AnomalyDetectionConfig.RateLimitConfig config = new AnomalyDetectionConfig.RateLimitConfig();
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getDefaultLimit()).isEqualTo(1000);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(3600);
        }
    }

    @Nested
    @DisplayName("RateLimitConfig setters")
    class RateLimitConfigSettersTest {

        @Test
        @DisplayName("setEnabled should update value")
        void setEnabledShouldUpdateValue() {
            AnomalyDetectionConfig.RateLimitConfig config = new AnomalyDetectionConfig.RateLimitConfig();
            config.setEnabled(false);
            assertThat(config.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("setDefaultLimit should update value")
        void setDefaultLimitShouldUpdateValue() {
            AnomalyDetectionConfig.RateLimitConfig config = new AnomalyDetectionConfig.RateLimitConfig();
            config.setDefaultLimit(2000);
            assertThat(config.getDefaultLimit()).isEqualTo(2000);
        }

        @Test
        @DisplayName("setTimeWindowSeconds should update value")
        void setTimeWindowSecondsShouldUpdateValue() {
            AnomalyDetectionConfig.RateLimitConfig config = new AnomalyDetectionConfig.RateLimitConfig();
            config.setTimeWindowSeconds(7200);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(7200);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            AnomalyDetectionConfig.RateLimitConfig config = new AnomalyDetectionConfig.RateLimitConfig();
            config.setEnabled(false);
            config.setDefaultLimit(500);
            config.setTimeWindowSeconds(1800);

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getDefaultLimit()).isEqualTo(500);
            assertThat(config.getTimeWindowSeconds()).isEqualTo(1800);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two BruteForceConfig instances with same values should be equal")
        void twoBruteForceConfigInstancesWithSameValuesShouldBeEqual() {
            AnomalyDetectionConfig.BruteForceConfig a = new AnomalyDetectionConfig.BruteForceConfig();
            a.setMaxFailuresPerUser(10);
            AnomalyDetectionConfig.BruteForceConfig b = new AnomalyDetectionConfig.BruteForceConfig();
            b.setMaxFailuresPerUser(10);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two BruteForceConfig instances with different values should not be equal")
        void twoBruteForceConfigInstancesWithDifferentValuesShouldNotBeEqual() {
            AnomalyDetectionConfig.BruteForceConfig a = new AnomalyDetectionConfig.BruteForceConfig();
            a.setMaxFailuresPerUser(10);
            AnomalyDetectionConfig.BruteForceConfig b = new AnomalyDetectionConfig.BruteForceConfig();
            b.setMaxFailuresPerUser(20);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("two AbnormalIpConfig instances with same values should be equal")
        void twoAbnormalIpConfigInstancesWithSameValuesShouldBeEqual() {
            AnomalyDetectionConfig.AbnormalIpConfig a = new AnomalyDetectionConfig.AbnormalIpConfig();
            a.setMaxIpChangesPer10Min(5);
            AnomalyDetectionConfig.AbnormalIpConfig b = new AnomalyDetectionConfig.AbnormalIpConfig();
            b.setMaxIpChangesPer10Min(5);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two RateLimitConfig instances with same values should be equal")
        void twoRateLimitConfigInstancesWithSameValuesShouldBeEqual() {
            AnomalyDetectionConfig.RateLimitConfig a = new AnomalyDetectionConfig.RateLimitConfig();
            a.setDefaultLimit(1000);
            AnomalyDetectionConfig.RateLimitConfig b = new AnomalyDetectionConfig.RateLimitConfig();
            b.setDefaultLimit(1000);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("BruteForceConfig toString should contain field names")
        void bruteForceConfigToStringShouldContainFieldNames() {
            AnomalyDetectionConfig.BruteForceConfig config = new AnomalyDetectionConfig.BruteForceConfig();
            String str = config.toString();

            assertThat(str).contains("BruteForceConfig");
            assertThat(str).contains("maxFailuresPerUser");
            assertThat(str).contains("maxFailuresPerIp");
        }

        @Test
        @DisplayName("AbnormalIpConfig toString should contain field names")
        void abnormalIpConfigToStringShouldContainFieldNames() {
            AnomalyDetectionConfig.AbnormalIpConfig config = new AnomalyDetectionConfig.AbnormalIpConfig();
            String str = config.toString();

            assertThat(str).contains("AbnormalIpConfig");
            assertThat(str).contains("maxIpChangesPer10Min");
        }

        @Test
        @DisplayName("RateLimitConfig toString should contain field names")
        void rateLimitConfigToStringShouldContainFieldNames() {
            AnomalyDetectionConfig.RateLimitConfig config = new AnomalyDetectionConfig.RateLimitConfig();
            String str = config.toString();

            assertThat(str).contains("RateLimitConfig");
            assertThat(str).contains("defaultLimit");
            assertThat(str).contains("timeWindowSeconds");
        }
    }
}
