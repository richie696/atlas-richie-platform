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
package com.richie.component.mfa.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaKeyUtilsTest {

    @Test
    void getUserCacheKey_withoutTenant() {
        assertThat(MfaKeyUtils.getUserCacheKey(null, "u1", false))
                .isEqualTo("mfa:user:u1");
    }

    @Test
    void getUserCacheKey_withTenantWhenEnabled() {
        assertThat(MfaKeyUtils.getUserCacheKey("t1", "u1", true))
                .isEqualTo("mfa:user:t1:u1");
    }

    @Test
    void getUserCacheKey_ignoresTenantWhenDisabled() {
        assertThat(MfaKeyUtils.getUserCacheKey("t1", "u1", false))
                .isEqualTo("mfa:user:u1");
    }

    @Test
    void getReplayPreventionKey_includesTimeStep() {
        assertThat(MfaKeyUtils.getReplayPreventionKey("t1", "u1", 170000L, true))
                .isEqualTo("mfa:used:t1:u1:170000");
    }

    @Test
    void getTrustedDeviceCacheKey_formatsDeviceSegment() {
        assertThat(MfaKeyUtils.getTrustedDeviceCacheKey(null, "u1", "device-9", false))
                .isEqualTo("mfa:trusted-device:u1:device-9");
    }

    @Test
    void getSyncLockKey_and_getFailureCountKey() {
        assertThat(MfaKeyUtils.getSyncLockKey("t1", "u1", true))
                .isEqualTo("mfa:sync:lock:t1:u1");
        assertThat(MfaKeyUtils.getFailureCountKey(null, "u1", false))
                .isEqualTo("mfa:failures:u1");
        assertThat(MfaKeyUtils.getTrustedDeviceListKey("t1", "u1", true))
                .isEqualTo("mfa:trusted-devices:t1:u1");
        assertThat(MfaKeyUtils.getSecretKeyCacheKey("tenant-a", "u2", true))
                .isEqualTo("mfa:secret:tenant-a:u2");
    }

    @Test
    void getFailureCountKey_withTenantEnabled() {
        assertThat(MfaKeyUtils.getFailureCountKey("t1", "u1", true))
                .isEqualTo("mfa:failures:t1:u1");
    }

    @Test
    void getReplayPreventionKey_withoutTenant() {
        assertThat(MfaKeyUtils.getReplayPreventionKey("t1", "u1", 170000L, false))
                .isEqualTo("mfa:used:u1:170000");
    }

    @Test
    void getSyncLockKey_withoutTenant() {
        assertThat(MfaKeyUtils.getSyncLockKey("t1", "u1", false))
                .isEqualTo("mfa:sync:lock:u1");
    }

    @Test
    void getTrustedDeviceCacheKey_withTenantEnabled() {
        assertThat(MfaKeyUtils.getTrustedDeviceCacheKey("t1", "u1", "device-9", true))
                .isEqualTo("mfa:trusted-device:t1:u1:device-9");
    }

    @Test
    void getTrustedDeviceListKey_withoutTenant() {
        assertThat(MfaKeyUtils.getTrustedDeviceListKey("t1", "u1", false))
                .isEqualTo("mfa:trusted-devices:u1");
    }
}
