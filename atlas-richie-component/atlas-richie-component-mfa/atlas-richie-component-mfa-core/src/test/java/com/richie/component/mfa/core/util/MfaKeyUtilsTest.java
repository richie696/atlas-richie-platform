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
    void getSecretKeyCacheKey_supportsTenantPrefix() {
        assertThat(MfaKeyUtils.getSecretKeyCacheKey("tenant-a", "u2", true))
                .isEqualTo("mfa:secret:tenant-a:u2");
    }
}
