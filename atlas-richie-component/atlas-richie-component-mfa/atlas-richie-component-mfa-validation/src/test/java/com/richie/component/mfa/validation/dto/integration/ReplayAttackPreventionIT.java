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
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import com.richie.component.mfa.validation.dto.support.AbstractMfaRedisIntegrationTest;
import com.richie.component.mfa.validation.replay.ReplayAttackPreventionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayAttackPreventionIT extends AbstractMfaRedisIntegrationTest {

    @Autowired
    private ReplayAttackPreventionService replayService;

    @Test
    void markCodeAsUsed_preventsReuse() {
        long timeStep = System.currentTimeMillis() / 1000 / 30;

        assertThat(replayService.isCodeUsed("user-rp", null, timeStep)).isFalse();
        replayService.markCodeAsUsed("user-rp", null, timeStep);
        assertThat(replayService.isCodeUsed("user-rp", null, timeStep)).isTrue();
    }

    @Test
    void userInfo_canBeReadFromCache() {
        MfaUserInfo userInfo = new MfaUserInfo();
        userInfo.setUserId("cache-user");
        userInfo.setStatus(MfaStatusEnum.ENABLED);
        String key = MfaKeyUtils.getUserCacheKey(null, "cache-user", false);
        GlobalCache.struct().set(key, userInfo, 60_000L);

        assertThat(GlobalCache.struct().get(key, MfaUserInfo.class))
                .extracting(MfaUserInfo::getUserId, MfaUserInfo::getStatus)
                .containsExactly("cache-user", MfaStatusEnum.ENABLED);
    }
}
