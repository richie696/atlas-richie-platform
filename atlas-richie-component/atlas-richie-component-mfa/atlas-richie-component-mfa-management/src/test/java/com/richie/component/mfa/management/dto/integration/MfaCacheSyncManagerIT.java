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
