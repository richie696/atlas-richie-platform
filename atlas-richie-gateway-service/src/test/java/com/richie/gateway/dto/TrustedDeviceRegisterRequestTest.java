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
package com.richie.gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TrustedDeviceRegisterRequest}.
 */
@DisplayName("TrustedDeviceRegisterRequest Tests")
class TrustedDeviceRegisterRequestTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all fields null by default")
        void shouldHaveAllFieldsNullByDefault() {
            TrustedDeviceRegisterRequest request = new TrustedDeviceRegisterRequest();
            assertThat(request.getTenantId()).isNull();
            assertThat(request.getUserId()).isNull();
            assertThat(request.getDeviceId()).isNull();
            assertThat(request.getDeviceName()).isNull();
            assertThat(request.getDeviceFingerprint()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            TrustedDeviceRegisterRequest request = new TrustedDeviceRegisterRequest();
            request.setTenantId("tenant-A");
            request.setUserId("user-001");
            request.setDeviceId("device-abc");
            request.setDeviceName("Chrome on MacBook Pro");
            request.setDeviceFingerprint("fp-hash-xyz");

            assertThat(request.getTenantId()).isEqualTo("tenant-A");
            assertThat(request.getUserId()).isEqualTo("user-001");
            assertThat(request.getDeviceId()).isEqualTo("device-abc");
            assertThat(request.getDeviceName()).isEqualTo("Chrome on MacBook Pro");
            assertThat(request.getDeviceFingerprint()).isEqualTo("fp-hash-xyz");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            TrustedDeviceRegisterRequest r1 = buildRequest("t", "u", "d", "n", "f");
            TrustedDeviceRegisterRequest r2 = buildRequest("t", "u", "d", "n", "f");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when tenantId differs")
        void shouldNotBeEqualWhenTenantIdDiffers() {
            TrustedDeviceRegisterRequest r1 = buildRequest("t1", "u", "d", "n", "f");
            TrustedDeviceRegisterRequest r2 = buildRequest("t2", "u", "d", "n", "f");

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when userId differs")
        void shouldNotBeEqualWhenUserIdDiffers() {
            TrustedDeviceRegisterRequest r1 = buildRequest("t", "u1", "d", "n", "f");
            TrustedDeviceRegisterRequest r2 = buildRequest("t", "u2", "d", "n", "f");

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when deviceId differs")
        void shouldNotBeEqualWhenDeviceIdDiffers() {
            TrustedDeviceRegisterRequest r1 = buildRequest("t", "u", "d1", "n", "f");
            TrustedDeviceRegisterRequest r2 = buildRequest("t", "u", "d2", "n", "f");

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when deviceFingerprint differs")
        void shouldNotBeEqualWhenDeviceFingerprintDiffers() {
            TrustedDeviceRegisterRequest r1 = buildRequest("t", "u", "d", "n", "f1");
            TrustedDeviceRegisterRequest r2 = buildRequest("t", "u", "d", "n", "f2");

            assertThat(r1).isNotEqualTo(r2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all field names in toString output")
        void shouldContainAllFieldNamesInToStringOutput() {
            TrustedDeviceRegisterRequest request = buildRequest("t", "u", "d", "n", "f");

            String str = request.toString();
            assertThat(str).contains("tenantId");
            assertThat(str).contains("userId");
            assertThat(str).contains("deviceId");
            assertThat(str).contains("deviceName");
            assertThat(str).contains("deviceFingerprint");
        }
    }

    private static TrustedDeviceRegisterRequest buildRequest(String tenantId, String userId, String deviceId,
                                                             String deviceName, String deviceFingerprint) {
        TrustedDeviceRegisterRequest request = new TrustedDeviceRegisterRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setDeviceId(deviceId);
        request.setDeviceName(deviceName);
        request.setDeviceFingerprint(deviceFingerprint);
        return request;
    }
}
