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

import com.richie.gateway.enums.EncryptTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AuthenticationConfig}.
 */
@DisplayName("AuthenticationConfig")
class AuthenticationConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            AuthenticationConfig config = new AuthenticationConfig();
            assertThat(config.getSecretKey()).isNull();
            assertThat(config.getEncryptType()).isEqualTo(EncryptTypeEnum.SM2);
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setSecretKey should update value")
        void setSecretKeyShouldUpdateValue() {
            AuthenticationConfig config = new AuthenticationConfig();
            config.setSecretKey("my-secret");
            assertThat(config.getSecretKey()).isEqualTo("my-secret");
        }

        @Test
        @DisplayName("setEncryptType should update value")
        void setEncryptTypeShouldUpdateValue() {
            AuthenticationConfig config = new AuthenticationConfig();
            config.setEncryptType(EncryptTypeEnum.MD5);
            assertThat(config.getEncryptType()).isEqualTo(EncryptTypeEnum.MD5);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            AuthenticationConfig config = new AuthenticationConfig();
            config.setSecretKey("custom-key");
            config.setEncryptType(EncryptTypeEnum.RSA);

            assertThat(config.getSecretKey()).isEqualTo("custom-key");
            assertThat(config.getEncryptType()).isEqualTo(EncryptTypeEnum.RSA);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            AuthenticationConfig a = new AuthenticationConfig();
            a.setSecretKey("key");
            a.setEncryptType(EncryptTypeEnum.MD5);
            AuthenticationConfig b = new AuthenticationConfig();
            b.setSecretKey("key");
            b.setEncryptType(EncryptTypeEnum.MD5);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            AuthenticationConfig a = new AuthenticationConfig();
            a.setSecretKey("key-a");
            AuthenticationConfig b = new AuthenticationConfig();
            b.setSecretKey("key-b");

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field names")
        void toStringShouldContainFieldNames() {
            AuthenticationConfig config = new AuthenticationConfig();
            String str = config.toString();

            assertThat(str).contains("AuthenticationConfig");
            assertThat(str).contains("secretKey");
            assertThat(str).contains("encryptType");
        }
    }
}
