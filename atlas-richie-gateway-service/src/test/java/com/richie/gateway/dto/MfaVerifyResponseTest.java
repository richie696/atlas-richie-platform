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
package com.richie.gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MfaVerifyResponse Tests")
class MfaVerifyResponseTest {

    @Nested
    @DisplayName("Builder Defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should have all fields null by default")
        void shouldHaveAllFieldsNullByDefault() {
            MfaVerifyResponse response = MfaVerifyResponse.builder().build();
            assertThat(response.getAccessToken()).isNull();
            assertThat(response.getTrustedDeviceRegistered()).isNull();
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("should build response with all fields")
        void shouldBuildResponseWithAllFields() {
            MfaVerifyResponse response = MfaVerifyResponse.builder()
                    .accessToken("access-token-xyz")
                    .trustedDeviceRegistered(true)
                    .build();

            assertThat(response.getAccessToken()).isEqualTo("access-token-xyz");
            assertThat(response.getTrustedDeviceRegistered()).isTrue();
        }

        @Test
        @DisplayName("should build with only accessToken")
        void shouldBuildWithOnlyAccessToken() {
            MfaVerifyResponse response = MfaVerifyResponse.builder()
                    .accessToken("access-token-xyz")
                    .build();

            assertThat(response.getAccessToken()).isEqualTo("access-token-xyz");
            assertThat(response.getTrustedDeviceRegistered()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            MfaVerifyResponse response = MfaVerifyResponse.builder().build();
            response.setAccessToken("access-token-abc");
            response.setTrustedDeviceRegistered(Boolean.FALSE);

            assertThat(response.getAccessToken()).isEqualTo("access-token-abc");
            assertThat(response.getTrustedDeviceRegistered()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when fields match")
        void shouldBeEqualWhenFieldsMatch() {
            MfaVerifyResponse r1 = MfaVerifyResponse.builder().accessToken("a").trustedDeviceRegistered(true).build();
            MfaVerifyResponse r2 = MfaVerifyResponse.builder().accessToken("a").trustedDeviceRegistered(true).build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when accessToken differs")
        void shouldNotBeEqualWhenAccessTokenDiffers() {
            MfaVerifyResponse r1 = MfaVerifyResponse.builder().accessToken("a1").build();
            MfaVerifyResponse r2 = MfaVerifyResponse.builder().accessToken("a2").build();

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when trustedDeviceRegistered differs")
        void shouldNotBeEqualWhenTrustedDeviceRegisteredDiffers() {
            MfaVerifyResponse r1 = MfaVerifyResponse.builder().accessToken("a").trustedDeviceRegistered(true).build();
            MfaVerifyResponse r2 = MfaVerifyResponse.builder().accessToken("a").trustedDeviceRegistered(false).build();

            assertThat(r1).isNotEqualTo(r2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all field names in toString output")
        void shouldContainAllFieldNamesInToStringOutput() {
            MfaVerifyResponse response = MfaVerifyResponse.builder()
                    .accessToken("a")
                    .trustedDeviceRegistered(true)
                    .build();

            String str = response.toString();
            assertThat(str).contains("accessToken");
            assertThat(str).contains("trustedDeviceRegistered");
        }
    }
}
