package com.richie.component.mfa.validation.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.CollectionOps;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import com.richie.component.mfa.core.entity.MfaTrustedDevice;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.validation.engine.TotpValidationEngine;
import com.richie.component.mfa.validation.replay.ReplayAttackPreventionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaValidationServiceImplTest {

    @Mock
    private MfaProperties properties;
    @Mock
    private TotpValidationEngine totpEngine;
    @Mock
    private ReplayAttackPreventionService replayService;
    @Mock
    private MfaTenantSupport tenantSupport;
    @Mock
    private KeyManagementProvider keyManagementProvider;
    @Mock
    private ValueOps valueOps;
    @Mock
    private KeyOps keyOps;
    @Mock
    private StructOps structOps;
    @Mock
    private CollectionOps collectionOps;

    private MfaValidationServiceImpl validationService;

    @BeforeEach
    void setUp() {
        MfaProperties real = new MfaProperties();
        when(properties.getTotp()).thenReturn(real.getTotp());
        when(properties.getSecurity()).thenReturn(real.getSecurity());
        when(tenantSupport.isTenantEnabled()).thenReturn(false);

        validationService = new MfaValidationServiceImpl(properties, totpEngine, replayService, tenantSupport);
        ReflectionTestUtils.setField(validationService, "keyManagementProvider", keyManagementProvider);
    }

    @Test
    void checkMfaStatus_blankUserId_returnsFailure() {
        var result = validationService.checkMfaStatus("", null, null);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_USER_ID");
    }

    @Test
    void verifyMfaCode_blankParams_returnsFailure() {
        var result = validationService.verifyMfaCode("", null, "123456");
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAMS");
    }

    @Test
    void checkTrustedDevice_blankParams_returnsFailure() {
        var result = validationService.checkTrustedDevice("", null, "device");
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PARAMS");
    }

    @Test
    void checkMfaStatus_noUserInCache_skipsMfa() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(null);

            var result = validationService.checkMfaStatus("u1", null, null);

            assertThat(result.isMfaRequired()).isFalse();
        }
    }

    @Test
    void verifyMfaCode_notEnabled_returnsFailure() {
        MfaUserInfo userInfo = new MfaUserInfo();
        userInfo.setStatus(MfaStatusEnum.DISABLED);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", null, "123456");

            assertThat(result.getErrorCode()).isEqualTo("MFA_NOT_ENABLED");
        }
    }

    @Test
    void verifyMfaCode_lockedAccount_returnsLocked() {
        MfaUserInfo userInfo = new MfaUserInfo();
        userInfo.setStatus(MfaStatusEnum.ENABLED);
        userInfo.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", null, "123456");

            assertThat(result.getErrorCode()).isEqualTo("ACCOUNT_LOCKED");
        }
    }

    @Test
    void verifyMfaCode_replayDetected_returnsCodeUsed() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), isNull(), anyLong())).thenReturn(true);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", null, "123456");

            assertThat(result.getErrorCode()).isEqualTo("MFA_CODE_USED");
        }
    }

    @Test
    void verifyMfaCode_validCode_succeeds() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), isNull(), anyLong())).thenReturn(false);
        when(keyManagementProvider.isAvailable()).thenReturn(true);
        when(keyManagementProvider.retrieveSecret("mfa/u1")).thenReturn("SECRET");
        when(totpEngine.verifyCode(eq("SECRET"), eq("654321"), eq("u1"), isNull(), anyInt(),
                eq("SHA1"), eq(30), eq(6))).thenReturn(true);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", null, "654321");

            assertThat(result.isSuccess()).isTrue();
            verify(replayService).markCodeAsUsed(eq("u1"), isNull(), anyLong());
            verify(keyOps).removeCache(anyString());
        }
    }

    @Test
    void checkTrustedDevice_validDevice_skipsMfa() {
        MfaTrustedDevice device = new MfaTrustedDevice();
        device.setTrustedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaTrustedDevice.class))).thenReturn(device);

            var result = validationService.checkTrustedDevice("u1", null, "dev-1");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isMfaRequired()).isFalse();
        }
    }

    @Test
    void checkTrustedDevice_expiredDevice_requiresMfa() {
        MfaTrustedDevice device = new MfaTrustedDevice();
        device.setTrustedUntil(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaTrustedDevice.class))).thenReturn(device);

            var result = validationService.checkTrustedDevice("u1", null, "dev-exp");

            assertThat(result.isTrustedDeviceExpired()).isTrue();
            assertThat(result.isMfaRequired()).isTrue();
        }
    }

    @Test
    void verifyMfaCode_userNotBound_returnsFailure() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(null);

            var result = validationService.verifyMfaCode("u1", null, "123456");

            assertThat(result.getErrorCode()).isEqualTo("MFA_NOT_BOUND");
        }
    }

    @Test
    void verifyMfaCode_invalidCode_incrementsFailureCount() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), isNull(), anyLong())).thenReturn(false);
        when(keyManagementProvider.isAvailable()).thenReturn(true);
        when(keyManagementProvider.retrieveSecret("mfa/u1")).thenReturn("SECRET");
        when(totpEngine.verifyCode(anyString(), anyString(), anyString(), isNull(), anyInt(),
                anyString(), anyInt(), anyInt())).thenReturn(false);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);
            when(valueOps.increment(anyString(), eq(1L), anyLong())).thenReturn(2L);

            var result = validationService.verifyMfaCode("u1", null, "000000");

            assertThat(result.getErrorCode()).isEqualTo("MFA_CODE_INVALID");
            assertThat(result.getFailureCount()).isEqualTo(2);
        }
    }

    @Test
    void verifyMfaCode_maxFailures_locksAccount() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), isNull(), anyLong())).thenReturn(false);
        when(keyManagementProvider.isAvailable()).thenReturn(true);
        when(keyManagementProvider.retrieveSecret("mfa/u1")).thenReturn("SECRET");
        when(totpEngine.verifyCode(anyString(), anyString(), anyString(), isNull(), anyInt(),
                anyString(), anyInt(), anyInt())).thenReturn(false);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);
            when(valueOps.increment(anyString(), eq(1L), anyLong())).thenReturn(5L);

            var result = validationService.verifyMfaCode("u1", null, "000000");

            assertThat(result.isAccountLocked()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo("MFA_CODE_INVALID");
            verify(valueOps).set(startsWith("mfa:lock:"), eq("1"), anyLong());
        }
    }

    @Test
    void verifyMfaCode_kmsUnavailable_returnsVerifyError() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), isNull(), anyLong())).thenReturn(false);
        when(keyManagementProvider.isAvailable()).thenReturn(false);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", null, "123456");

            assertThat(result.getErrorCode()).isEqualTo("MFA_VERIFY_ERROR");
        }
    }

    @Test
    void checkMfaStatus_enabledUser_returnsChallengeMetadata() {
        MfaUserInfo userInfo = enabledUser();

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(null);

            var result = validationService.checkMfaStatus("u1", null, null);

            assertThat(result.isMfaRequired()).isTrue();
            assertThat(result.getMaxTrustedDevices()).isEqualTo(10);
            assertThat(result.getDefaultTrustDays()).isEqualTo(30);
        }
    }

    @Test
    void checkMfaStatus_countsActiveTrustedDevices() {
        MfaUserInfo userInfo = enabledUser();
        MfaTrustedDevice device = new MfaTrustedDevice();
        device.setTrustedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of("d1"));
            when(structOps.get(contains("d1"), eq(MfaTrustedDevice.class))).thenReturn(device);

            var result = validationService.checkMfaStatus("u1", null, null);

            assertThat(result.getTrustedDeviceCount()).isEqualTo(1);
        }
    }

    @Test
    void checkTrustedDevice_missingDevice_requiresMfa() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaTrustedDevice.class))).thenReturn(null);

            var result = validationService.checkTrustedDevice("u1", null, "dev-missing");

            assertThat(result.isMfaRequired()).isTrue();
            assertThat(result.isTrustedDevice()).isFalse();
        }
    }

    @Test
    void checkMfaStatus_lockedUser_returnsLocked() {
        MfaUserInfo userInfo = enabledUser();
        userInfo.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.checkMfaStatus("u1", null, null);

            assertThat(result.isAccountLocked()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo("ACCOUNT_LOCKED");
        }
    }

    @Test
    void verifyMfaCode_nullStatus_returnsMfaNotEnabled() {
        MfaUserInfo userInfo = new MfaUserInfo();
        // Do not set status - tests null-status branch at lines 158-163

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", null, "123456");

            assertThat(result.getErrorCode()).isEqualTo("MFA_NOT_ENABLED");
        }
    }

    @Test
    void verifyMfaCode_kmsThrowsException_returnsVerifyError() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), isNull(), anyLong())).thenReturn(false);
        when(keyManagementProvider.isAvailable()).thenReturn(true);
        when(keyManagementProvider.retrieveSecret(anyString())).thenThrow(new RuntimeException("simulated KMS error"));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", null, "123456");

            assertThat(result.getErrorCode()).isEqualTo("MFA_VERIFY_ERROR");
        }
    }

    @Test
    void verifyMfaCode_withTenantId_usesTenantInSecretReference() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), eq("tenant-1"), anyLong())).thenReturn(false);
        when(keyManagementProvider.isAvailable()).thenReturn(true);
        when(keyManagementProvider.retrieveSecret("mfa/tenant-1/u1")).thenReturn("SECRET");
        when(totpEngine.verifyCode(eq("SECRET"), eq("654321"), eq("u1"), eq("tenant-1"), anyInt(),
                eq("SHA1"), eq(30), eq(6))).thenReturn(true);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);

            var result = validationService.verifyMfaCode("u1", "tenant-1", "654321");

            assertThat(result.isSuccess()).isTrue();
            verify(keyManagementProvider).retrieveSecret("mfa/tenant-1/u1");
        }
    }

    @Test
    void checkTrustedDevice_nullDevice_returnsMfaRequired() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(anyString(), eq(MfaTrustedDevice.class))).thenReturn(null);

            var result = validationService.checkTrustedDevice("u1", null, "unknown-device");

            assertThat(result.isMfaRequired()).isTrue();
            assertThat(result.isTrustedDevice()).isFalse();
        }
    }

    @Test
    void verifyMfaCode_withEventPublisherAndRequestContext_publishesAuditEvent() {
        MfaUserInfo userInfo = enabledUser();
        when(replayService.isCodeUsed(eq("u1"), isNull(), anyLong())).thenReturn(false);
        when(keyManagementProvider.isAvailable()).thenReturn(true);
        when(keyManagementProvider.retrieveSecret("mfa/u1")).thenReturn("SECRET");
        when(totpEngine.verifyCode(anyString(), anyString(), anyString(), isNull(), anyInt(),
                anyString(), anyInt(), anyInt())).thenReturn(false);

        ApplicationEventPublisher mockEventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(validationService, "eventPublisher", mockEventPublisher);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.168.1.100");
        mockRequest.addHeader("User-Agent", "TestBrowser/1.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(structOps.get(anyString(), eq(MfaUserInfo.class))).thenReturn(userInfo);
            when(valueOps.increment(anyString(), eq(1L), anyLong())).thenReturn(1L);

            var result = validationService.verifyMfaCode("u1", null, "000000");

            assertThat(result.getErrorCode()).isEqualTo("MFA_CODE_INVALID");
            verify(mockEventPublisher).publishEvent(any());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static MfaUserInfo enabledUser() {
        MfaUserInfo userInfo = new MfaUserInfo();
        userInfo.setStatus(MfaStatusEnum.ENABLED);
        userInfo.setAlgorithm("SHA1");
        userInfo.setPeriod(30);
        userInfo.setDigits(6);
        return userInfo;
    }
}
