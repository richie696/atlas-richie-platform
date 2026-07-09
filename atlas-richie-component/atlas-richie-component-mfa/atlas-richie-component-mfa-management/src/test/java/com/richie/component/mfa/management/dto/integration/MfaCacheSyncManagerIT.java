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
package com.richie.component.mfa.management.dto.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.richie.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import com.richie.component.mfa.management.dto.support.AbstractMfaRedisIntegrationTest;
import com.richie.component.mfa.management.manager.MfaCacheSyncManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MfaCacheSyncManagerIT extends AbstractMfaRedisIntegrationTest {

    @Autowired
    private MfaCacheSyncManager cacheSyncManager;

    @Autowired
    private LocalKeyManagementEngine keyManagementEngine;

    @Test
    void syncAndRemoveUserInfo_updatesRedis() {
        MfaUserInfo userInfo = new MfaUserInfo();
        userInfo.setUserId("mgmt-user");
        userInfo.setStatus(MfaStatusEnum.ENABLED);
        cacheSyncManager.syncToCache(userInfo);

        String key = MfaKeyUtils.getUserCacheKey(null, "mgmt-user", false);
        assertThat(GlobalCache.struct().get(key, MfaUserInfo.class).getStatus()).isEqualTo(MfaStatusEnum.ENABLED);

        cacheSyncManager.removeFromCache(null, "mgmt-user");
        assertThat(GlobalCache.key().hasKey(key)).isFalse();
    }

    @Test
    void secretKey_roundTripThroughRedis() {
        String reference = keyManagementEngine.storeSecret(null, "mgmt-secret-user", "JBSWY3DPEHPK3PXP");
        assertThat(keyManagementEngine.retrieveSecret(reference)).isEqualTo("JBSWY3DPEHPK3PXP");
    }
}
