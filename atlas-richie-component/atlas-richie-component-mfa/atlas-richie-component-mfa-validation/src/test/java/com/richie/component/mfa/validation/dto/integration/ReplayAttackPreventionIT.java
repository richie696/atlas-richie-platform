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
