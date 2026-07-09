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
package com.richie.component.mfa.validation.dto.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.richie.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import com.richie.component.mfa.validation.dto.support.AbstractMfaRedisIntegrationTest;
import com.richie.component.mfa.validation.replay.ReplayAttackPreventionService;
import com.richie.component.mfa.validation.service.impl.MfaValidationServiceImpl;
import org.apache.commons.codec.binary.Base32;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MfaValidationServiceIT extends AbstractMfaRedisIntegrationTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Autowired
    private MfaValidationServiceImpl validationService;

    @Autowired
    private LocalKeyManagementEngine keyManagementEngine;

    @Autowired
    private ReplayAttackPreventionService replayService;

    @BeforeEach
    void seedEnabledUser() {
        keyManagementEngine.storeSecret(null, "val-user", SECRET);
    }

    @Test
    void checkMfaStatus_noCacheEntry_skipsMfa() {
        var result = validationService.checkMfaStatus("unknown-user", null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMfaRequired()).isFalse();
    }

    @Test
    void checkMfaStatus_enabledUser_requiresMfa() {
        putUser("status-user", MfaStatusEnum.ENABLED);

        var result = validationService.checkMfaStatus("status-user", null, null);

        assertThat(result.isMfaRequired()).isTrue();
        assertThat(result.isMfaBound()).isTrue();
    }

    @Test
    void checkMfaStatus_disabledStatus_skipsMfa() {
        putUser("disabled-user", MfaStatusEnum.DISABLED);

        var result = validationService.checkMfaStatus("disabled-user", null, null);

        assertThat(result.isMfaRequired()).isFalse();
    }

    @Test
    void verifyMfaCode_userNotBound_returnsFailure() {
        var result = validationService.verifyMfaCode("nobody", null, "123456");

        assertThat(result.getErrorCode()).isEqualTo("MFA_NOT_BOUND");
    }

    @Test
    void verifyMfaCode_validTotp_succeeds() {
        putUser("val-user", MfaStatusEnum.ENABLED);
        String code = totpAt(Instant.now().getEpochSecond(), 30, 6);

        var result = validationService.verifyMfaCode("val-user", null, code);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMfaBound()).isTrue();
    }

    @Test
    void verifyMfaCode_wrongCode_returnsInvalid() {
        putUser("val-user", MfaStatusEnum.ENABLED);

        var result = validationService.verifyMfaCode("val-user", null, "000000");

        assertThat(result.getErrorCode()).isEqualTo("MFA_CODE_INVALID");
        assertThat(result.getFailureCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void verifyMfaCode_replayAttack_isRejected() {
        putUser("val-user", MfaStatusEnum.ENABLED);
        long timeStep = System.currentTimeMillis() / 1000 / 30;
        replayService.markCodeAsUsed("val-user", null, timeStep);

        var result = validationService.verifyMfaCode("val-user", null, "123456");

        assertThat(result.getErrorCode()).isEqualTo("MFA_CODE_USED");
    }

    @Test
    void checkTrustedDevice_missingDevice_requiresMfa() {
        var result = validationService.checkTrustedDevice("td-user", null, "device-1");

        assertThat(result.isMfaRequired()).isTrue();
        assertThat(result.isTrustedDevice()).isFalse();
    }

    private void putUser(String userId, MfaStatusEnum status) {
        MfaUserInfo userInfo = new MfaUserInfo();
        userInfo.setUserId(userId);
        userInfo.setStatus(status);
        userInfo.setPeriod(30);
        userInfo.setDigits(6);
        userInfo.setAlgorithm("SHA1");
        String key = MfaKeyUtils.getUserCacheKey(null, userId, false);
        GlobalCache.struct().set(key, userInfo, 60_000L);
    }

    private static String totpAt(long epochSeconds, int period, int digits) {
        long counter = epochSeconds / period;
        byte[] key = new Base32().decode(SECRET);
        byte[] data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array();
        Mac hmac = new HMac(new SHA1Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] hash = new byte[hmac.getMacSize()];
        hmac.doFinal(hash, 0);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }
}
