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
 * Tests for {@link EccCryptoConfig}.
 */
@DisplayName("EccCryptoConfig")
class EccCryptoConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            EccCryptoConfig config = new EccCryptoConfig();
            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getGatewayPrivateKey()).isNull();
            assertThat(config.getGatewayPublicKey()).isNull();
            assertThat(config.getEncryptPaths()).containsExactly("/api/**");
            assertThat(config.getExcludePaths()).containsExactly("/api/health/**", "/api/public/**");
            assertThat(config.getGatewayKeyExpire()).isEqualTo(6);
            assertThat(config.getClientKeyCacheExpire()).isEqualTo(3600L);
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setEnabled should update value")
        void setEnabledShouldUpdateValue() {
            EccCryptoConfig config = new EccCryptoConfig();
            config.setEnabled(true);
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("setGatewayPrivateKey should update value")
        void setGatewayPrivateKeyShouldUpdateValue() {
            EccCryptoConfig config = new EccCryptoConfig();
            config.setGatewayPrivateKey("privateKey123");
            assertThat(config.getGatewayPrivateKey()).isEqualTo("privateKey123");
        }

        @Test
        @DisplayName("setGatewayPublicKey should update value")
        void setGatewayPublicKeyShouldUpdateValue() {
            EccCryptoConfig config = new EccCryptoConfig();
            config.setGatewayPublicKey("publicKey456");
            assertThat(config.getGatewayPublicKey()).isEqualTo("publicKey456");
        }

        @Test
        @DisplayName("setEncryptPaths should update value")
        void setEncryptPathsShouldUpdateValue() {
            EccCryptoConfig config = new EccCryptoConfig();
            String[] paths = {"/secure/**"};
            config.setEncryptPaths(paths);
            assertThat(config.getEncryptPaths()).isEqualTo(paths);
        }

        @Test
        @DisplayName("setExcludePaths should update value")
        void setExcludePathsShouldUpdateValue() {
            EccCryptoConfig config = new EccCryptoConfig();
            String[] paths = {"/public/**"};
            config.setExcludePaths(paths);
            assertThat(config.getExcludePaths()).isEqualTo(paths);
        }

        @Test
        @DisplayName("setGatewayKeyExpire should update value")
        void setGatewayKeyExpireShouldUpdateValue() {
            EccCryptoConfig config = new EccCryptoConfig();
            config.setGatewayKeyExpire(12);
            assertThat(config.getGatewayKeyExpire()).isEqualTo(12);
        }

        @Test
        @DisplayName("setClientKeyCacheExpire should update value")
        void setClientKeyCacheExpireShouldUpdateValue() {
            EccCryptoConfig config = new EccCryptoConfig();
            config.setClientKeyCacheExpire(7200L);
            assertThat(config.getClientKeyCacheExpire()).isEqualTo(7200L);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            EccCryptoConfig config = new EccCryptoConfig();
            config.setEnabled(true);
            config.setGatewayPrivateKey("pk");
            config.setGatewayPublicKey("pub");
            config.setEncryptPaths(new String[]{"/enc/**"});
            config.setExcludePaths(new String[]{"/exc/**"});
            config.setGatewayKeyExpire(24);
            config.setClientKeyCacheExpire(1800L);

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getGatewayPrivateKey()).isEqualTo("pk");
            assertThat(config.getGatewayPublicKey()).isEqualTo("pub");
            assertThat(config.getEncryptPaths()).containsExactly("/enc/**");
            assertThat(config.getExcludePaths()).containsExactly("/exc/**");
            assertThat(config.getGatewayKeyExpire()).isEqualTo(24);
            assertThat(config.getClientKeyCacheExpire()).isEqualTo(1800L);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            EccCryptoConfig a = new EccCryptoConfig();
            a.setEnabled(true).setGatewayKeyExpire(12);
            EccCryptoConfig b = new EccCryptoConfig();
            b.setEnabled(true).setGatewayKeyExpire(12);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            EccCryptoConfig a = new EccCryptoConfig();
            a.setEnabled(true);
            EccCryptoConfig b = new EccCryptoConfig();
            b.setEnabled(false);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field names")
        void toStringShouldContainFieldNames() {
            EccCryptoConfig config = new EccCryptoConfig();
            String str = config.toString();

            assertThat(str).contains("EccCryptoConfig");
            assertThat(str).contains("enabled");
            assertThat(str).contains("gatewayKeyExpire");
        }
    }

    @Nested
    @DisplayName("Lombok @Data and @Accessors")
    class LombokTest {

        @Test
        @DisplayName("@Data with chain=true should enable fluent setters")
        void dataWithChainShouldEnableFluentSetters() {
            EccCryptoConfig config = new EccCryptoConfig();
            EccCryptoConfig result = config.setEnabled(true).setGatewayKeyExpire(48);

            assertThat(result).isSameAs(config);
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getGatewayKeyExpire()).isEqualTo(48);
        }
    }
}
