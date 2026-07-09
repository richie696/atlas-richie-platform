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
 * Tests for {@link SsoConfig} and its nested {@link SsoConfig.SsoPortalConfig}.
 */
@DisplayName("SsoConfig")
class SsoConfigTest {

    @Nested
    @DisplayName("SsoConfig defaults")
    class SsoConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            SsoConfig config = new SsoConfig();
            assertThat(config.isEnable()).isFalse();
            assertThat(config.getSsoLoginUrl()).isEqualTo("http://localhost:8080/login");
            assertThat(config.getOnlineTokenPath()).isEqualTo("platform:gateway:last-online-token:");
            assertThat(config.getPortal()).isNotNull();
        }
    }

    @Nested
    @DisplayName("SsoConfig setters")
    class SsoConfigSettersTest {

        @Test
        @DisplayName("setEnable should update value")
        void setEnableShouldUpdateValue() {
            SsoConfig config = new SsoConfig();
            config.setEnable(true);
            assertThat(config.isEnable()).isTrue();
        }

        @Test
        @DisplayName("setSsoLoginUrl should update value")
        void setSsoLoginUrlShouldUpdateValue() {
            SsoConfig config = new SsoConfig();
            config.setSsoLoginUrl("https://sso.example.com/login");
            assertThat(config.getSsoLoginUrl()).isEqualTo("https://sso.example.com/login");
        }

        @Test
        @DisplayName("setOnlineTokenPath should update value")
        void setOnlineTokenPathShouldUpdateValue() {
            SsoConfig config = new SsoConfig();
            config.setOnlineTokenPath("custom:path:");
            assertThat(config.getOnlineTokenPath()).isEqualTo("custom:path:");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            SsoConfig config = new SsoConfig();
            config.setEnable(true);
            config.setSsoLoginUrl("https://custom.example.com/login");
            config.setOnlineTokenPath("custom:online:token:");

            assertThat(config.isEnable()).isTrue();
            assertThat(config.getSsoLoginUrl()).isEqualTo("https://custom.example.com/login");
            assertThat(config.getOnlineTokenPath()).isEqualTo("custom:online:token:");
        }
    }

    @Nested
    @DisplayName("SsoPortalConfig defaults")
    class SsoPortalConfigDefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            SsoConfig.SsoPortalConfig portal = new SsoConfig.SsoPortalConfig();
            assertThat(portal.isEnable()).isFalse();
            assertThat(portal.getCheckTokenUrl()).isEqualTo("http://localhost:8080/sign/authz/oauth/v20/check_token");
        }
    }

    @Nested
    @DisplayName("SsoPortalConfig setters")
    class SsoPortalConfigSettersTest {

        @Test
        @DisplayName("setEnable should update value")
        void setEnableShouldUpdateValue() {
            SsoConfig.SsoPortalConfig portal = new SsoConfig.SsoPortalConfig();
            portal.setEnable(true);
            assertThat(portal.isEnable()).isTrue();
        }

        @Test
        @DisplayName("setCheckTokenUrl should update value")
        void setCheckTokenUrlShouldUpdateValue() {
            SsoConfig.SsoPortalConfig portal = new SsoConfig.SsoPortalConfig();
            portal.setCheckTokenUrl("https://sso.example.com/check");
            assertThat(portal.getCheckTokenUrl()).isEqualTo("https://sso.example.com/check");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            SsoConfig.SsoPortalConfig portal = new SsoConfig.SsoPortalConfig();
            portal.setEnable(true);
            portal.setCheckTokenUrl("https://custom.example.com/check");

            assertThat(portal.isEnable()).isTrue();
            assertThat(portal.getCheckTokenUrl()).isEqualTo("https://custom.example.com/check");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two SsoConfig instances with same values should be equal")
        void twoSsoConfigInstancesWithSameValuesShouldBeEqual() {
            SsoConfig a = new SsoConfig();
            a.setEnable(true);
            a.setSsoLoginUrl("https://a.com");
            SsoConfig b = new SsoConfig();
            b.setEnable(true);
            b.setSsoLoginUrl("https://a.com");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two SsoConfig instances with different values should not be equal")
        void twoSsoConfigInstancesWithDifferentValuesShouldNotBeEqual() {
            SsoConfig a = new SsoConfig();
            a.setEnable(true);
            SsoConfig b = new SsoConfig();
            b.setEnable(false);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("two SsoPortalConfig instances with same values should be equal")
        void twoSsoPortalConfigInstancesWithSameValuesShouldBeEqual() {
            SsoConfig.SsoPortalConfig a = new SsoConfig.SsoPortalConfig();
            a.setEnable(true);
            a.setCheckTokenUrl("https://check.com");
            SsoConfig.SsoPortalConfig b = new SsoConfig.SsoPortalConfig();
            b.setEnable(true);
            b.setCheckTokenUrl("https://check.com");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("SsoConfig toString should contain field names")
        void ssoConfigToStringShouldContainFieldNames() {
            SsoConfig config = new SsoConfig();
            String str = config.toString();

            assertThat(str).contains("SsoConfig");
            assertThat(str).contains("enable");
            assertThat(str).contains("ssoLoginUrl");
        }

        @Test
        @DisplayName("SsoPortalConfig toString should contain field names")
        void ssoPortalConfigToStringShouldContainFieldNames() {
            SsoConfig.SsoPortalConfig portal = new SsoConfig.SsoPortalConfig();
            String str = portal.toString();

            assertThat(str).contains("SsoPortalConfig");
            assertThat(str).contains("enable");
            assertThat(str).contains("checkTokenUrl");
        }
    }
}
