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
package com.richie.gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MfaRequiredResponse Tests")
class MfaRequiredResponseTest {

    @Nested
    @DisplayName("Builder Defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should have all outer fields null by default")
        void shouldHaveAllOuterFieldsNullByDefault() {
            MfaRequiredResponse response = MfaRequiredResponse.builder().build();
            assertThat(response.getCode()).isNull();
            assertThat(response.getMsg()).isNull();
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("should have all nested data fields null by default")
        void shouldHaveAllNestedDataFieldsNullByDefault() {
            MfaRequiredResponse.MfaRequiredData data = MfaRequiredResponse.MfaRequiredData.builder().build();
            assertThat(data.getMfaToken()).isNull();
            assertThat(data.getMfaMethods()).isNull();
            assertThat(data.getTrustedDeviceSupported()).isNull();
            assertThat(data.getTrustedDeviceCount()).isNull();
            assertThat(data.getMaxTrustedDevices()).isNull();
            assertThat(data.getDefaultTrustDays()).isNull();
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("should build outer response with all fields")
        void shouldBuildOuterResponseWithAllFields() {
            MfaRequiredResponse.MfaRequiredData data = MfaRequiredResponse.MfaRequiredData.builder()
                    .mfaToken("mfa-temp")
                    .mfaMethods(List.of("TOTP"))
                    .trustedDeviceSupported(true)
                    .trustedDeviceCount(2)
                    .maxTrustedDevices(5)
                    .defaultTrustDays(30)
                    .build();

            MfaRequiredResponse response = MfaRequiredResponse.builder()
                    .code("MFA_REQUIRED")
                    .msg("MFA verification required")
                    .data(data)
                    .build();

            assertThat(response.getCode()).isEqualTo("MFA_REQUIRED");
            assertThat(response.getMsg()).isEqualTo("MFA verification required");
            assertThat(response.getData()).isSameAs(data);
        }

        @Test
        @DisplayName("should build with only code and msg")
        void shouldBuildWithOnlyCodeAndMsg() {
            MfaRequiredResponse response = MfaRequiredResponse.builder()
                    .code("MFA_REQUIRED")
                    .msg("Required")
                    .build();

            assertThat(response.getCode()).isEqualTo("MFA_REQUIRED");
            assertThat(response.getMsg()).isEqualTo("Required");
            assertThat(response.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all outer fields")
        void shouldSetAndGetAllOuterFields() {
            MfaRequiredResponse.MfaRequiredData data = MfaRequiredResponse.MfaRequiredData.builder()
                    .mfaToken("temp-token").build();

            MfaRequiredResponse response = MfaRequiredResponse.builder().build();
            response.setCode("C");
            response.setMsg("M");
            response.setData(data);

            assertThat(response.getCode()).isEqualTo("C");
            assertThat(response.getMsg()).isEqualTo("M");
            assertThat(response.getData()).isSameAs(data);
        }

        @Test
        @DisplayName("should set and get all nested data fields")
        void shouldSetAndGetAllNestedDataFields() {
            MfaRequiredResponse.MfaRequiredData data = MfaRequiredResponse.MfaRequiredData.builder().build();
            data.setMfaToken("mfa-temp");
            data.setMfaMethods(List.of("TOTP", "BACKUP_CODE"));
            data.setTrustedDeviceSupported(Boolean.TRUE);
            data.setTrustedDeviceCount(3);
            data.setMaxTrustedDevices(10);
            data.setDefaultTrustDays(7);

            assertThat(data.getMfaToken()).isEqualTo("mfa-temp");
            assertThat(data.getMfaMethods()).containsExactly("TOTP", "BACKUP_CODE");
            assertThat(data.getTrustedDeviceSupported()).isTrue();
            assertThat(data.getTrustedDeviceCount()).isEqualTo(3);
            assertThat(data.getMaxTrustedDevices()).isEqualTo(10);
            assertThat(data.getDefaultTrustDays()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when outer fields match")
        void shouldBeEqualWhenOuterFieldsMatch() {
            MfaRequiredResponse r1 = MfaRequiredResponse.builder().code("C").msg("M").build();
            MfaRequiredResponse r2 = MfaRequiredResponse.builder().code("C").msg("M").build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when outer code differs")
        void shouldNotBeEqualWhenOuterCodeDiffers() {
            MfaRequiredResponse r1 = MfaRequiredResponse.builder().code("A").build();
            MfaRequiredResponse r2 = MfaRequiredResponse.builder().code("B").build();

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should be equal for nested data when fields match")
        void shouldBeEqualForNestedDataWhenFieldsMatch() {
            MfaRequiredResponse.MfaRequiredData d1 = MfaRequiredResponse.MfaRequiredData.builder()
                    .mfaToken("t").mfaMethods(List.of("TOTP")).trustedDeviceCount(1).build();
            MfaRequiredResponse.MfaRequiredData d2 = MfaRequiredResponse.MfaRequiredData.builder()
                    .mfaToken("t").mfaMethods(List.of("TOTP")).trustedDeviceCount(1).build();

            assertThat(d1).isEqualTo(d2);
            assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("outer toString should contain field names")
        void outerToStringShouldContainFieldNames() {
            MfaRequiredResponse response = MfaRequiredResponse.builder().code("C").msg("M").build();
            String str = response.toString();

            assertThat(str).contains("code");
            assertThat(str).contains("msg");
            assertThat(str).contains("data");
        }

        @Test
        @DisplayName("nested data toString should contain field names")
        void nestedDataToStringShouldContainFieldNames() {
            MfaRequiredResponse.MfaRequiredData data = MfaRequiredResponse.MfaRequiredData.builder()
                    .mfaToken("t")
                    .mfaMethods(List.of("TOTP"))
                    .trustedDeviceSupported(true)
                    .trustedDeviceCount(1)
                    .maxTrustedDevices(5)
                    .defaultTrustDays(30)
                    .build();

            String str = data.toString();
            assertThat(str).contains("mfaToken");
            assertThat(str).contains("mfaMethods");
            assertThat(str).contains("trustedDeviceSupported");
            assertThat(str).contains("trustedDeviceCount");
            assertThat(str).contains("maxTrustedDevices");
            assertThat(str).contains("defaultTrustDays");
        }
    }
}
