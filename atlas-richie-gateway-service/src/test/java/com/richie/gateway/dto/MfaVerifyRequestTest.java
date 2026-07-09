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

/**
 * Tests for {@link MfaVerifyRequest}.
 */
@DisplayName("MfaVerifyRequest Tests")
class MfaVerifyRequestTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all fields null by default")
        void shouldHaveAllFieldsNullByDefault() {
            MfaVerifyRequest request = new MfaVerifyRequest();
            assertThat(request.getMfaToken()).isNull();
            assertThat(request.getMfaCode()).isNull();
            assertThat(request.getDeviceId()).isNull();
            assertThat(request.getDeviceFingerprint()).isNull();
            assertThat(request.getTrustDevice()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            MfaVerifyRequest request = new MfaVerifyRequest();
            request.setMfaToken("mfa-temp-token");
            request.setMfaCode("123456");
            request.setDeviceId("device-abc");
            request.setDeviceFingerprint("fp-hash-xyz");
            request.setTrustDevice(Boolean.TRUE);

            assertThat(request.getMfaToken()).isEqualTo("mfa-temp-token");
            assertThat(request.getMfaCode()).isEqualTo("123456");
            assertThat(request.getDeviceId()).isEqualTo("device-abc");
            assertThat(request.getDeviceFingerprint()).isEqualTo("fp-hash-xyz");
            assertThat(request.getTrustDevice()).isTrue();
        }

        @Test
        @DisplayName("should allow false trustDevice value")
        void shouldAllowFalseTrustDeviceValue() {
            MfaVerifyRequest request = new MfaVerifyRequest();
            request.setTrustDevice(Boolean.FALSE);

            assertThat(request.getTrustDevice()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            MfaVerifyRequest r1 = buildRequest("t", "c", "d", "f", true);
            MfaVerifyRequest r2 = buildRequest("t", "c", "d", "f", true);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when mfaToken differs")
        void shouldNotBeEqualWhenMfaTokenDiffers() {
            MfaVerifyRequest r1 = buildRequest("t1", "c", "d", "f", true);
            MfaVerifyRequest r2 = buildRequest("t2", "c", "d", "f", true);

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when mfaCode differs")
        void shouldNotBeEqualWhenMfaCodeDiffers() {
            MfaVerifyRequest r1 = buildRequest("t", "c1", "d", "f", true);
            MfaVerifyRequest r2 = buildRequest("t", "c2", "d", "f", true);

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when trustDevice differs")
        void shouldNotBeEqualWhenTrustDeviceDiffers() {
            MfaVerifyRequest r1 = buildRequest("t", "c", "d", "f", true);
            MfaVerifyRequest r2 = buildRequest("t", "c", "d", "f", false);

            assertThat(r1).isNotEqualTo(r2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all field names in toString output")
        void shouldContainAllFieldNamesInToStringOutput() {
            MfaVerifyRequest request = buildRequest("t", "c", "d", "f", true);

            String str = request.toString();
            assertThat(str).contains("mfaToken");
            assertThat(str).contains("mfaCode");
            assertThat(str).contains("deviceId");
            assertThat(str).contains("deviceFingerprint");
            assertThat(str).contains("trustDevice");
        }
    }

    private static MfaVerifyRequest buildRequest(String mfaToken, String mfaCode, String deviceId,
                                                 String deviceFingerprint, Boolean trustDevice) {
        MfaVerifyRequest request = new MfaVerifyRequest();
        request.setMfaToken(mfaToken);
        request.setMfaCode(mfaCode);
        request.setDeviceId(deviceId);
        request.setDeviceFingerprint(deviceFingerprint);
        request.setTrustDevice(trustDevice);
        return request;
    }
}
