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
package com.richie.component.oauth.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantTypeTest {

    @Test
    void clientCredentials_hasCorrectValue() {
        assertThat(GrantType.CLIENT_CREDENTIALS.getValue()).isEqualTo("client_credentials");
    }

    @Test
    void refreshToken_hasCorrectValue() {
        assertThat(GrantType.REFRESH_TOKEN.getValue()).isEqualTo("refresh_token");
    }

    @Test
    void authorizationCode_hasCorrectValue() {
        assertThat(GrantType.AUTHORIZATION_CODE.getValue()).isEqualTo("authorization_code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"client_credentials", "refresh_token", "authorization_code"})
    void fromValue_whenValidValue_returnsEnum(String value) {
        GrantType result = GrantType.fromValue(value);
        assertThat(result).isNotNull();
    }

    @Test
    void fromValue_whenClientCredentials_returnsClientCredentials() {
        GrantType result = GrantType.fromValue("client_credentials");
        assertThat(result).isEqualTo(GrantType.CLIENT_CREDENTIALS);
    }

    @Test
    void fromValue_whenRefreshToken_returnsRefreshToken() {
        GrantType result = GrantType.fromValue("refresh_token");
        assertThat(result).isEqualTo(GrantType.REFRESH_TOKEN);
    }

    @Test
    void fromValue_whenAuthorizationCode_returnsAuthorizationCode() {
        GrantType result = GrantType.fromValue("authorization_code");
        assertThat(result).isEqualTo(GrantType.AUTHORIZATION_CODE);
    }

    @Test
    void fromValue_whenUnsupportedValue_throwsException() {
        assertThatThrownBy(() -> GrantType.fromValue("unsupported"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported grant_type");
    }

    @Test
    void values_returnsAllEnumValues() {
        GrantType[] values = GrantType.values();
        assertThat(values).hasSize(3);
        assertThat(values).contains(GrantType.CLIENT_CREDENTIALS, GrantType.REFRESH_TOKEN, GrantType.AUTHORIZATION_CODE);
    }
}
