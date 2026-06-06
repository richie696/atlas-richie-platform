package com.richie.component.mfa.validation.replay;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayAttackPreventionServiceTest {

    @Mock
    private KeyOps keyOps;
    @Mock
    private ValueOps valueOps;

    private ReplayAttackPreventionService replayService;

    @BeforeEach
    void setUp() {
        replayService = new ReplayAttackPreventionService(new MfaProperties(), new MfaTenantSupport());
    }

    @Test
    void isCodeUsed_reflectsCacheKeyPresence() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(keyOps.hasKey(anyString())).thenReturn(false, true);

            assertThat(replayService.isCodeUsed("user-a", null, 42L)).isFalse();
            assertThat(replayService.isCodeUsed("user-a", null, 42L)).isTrue();
        }
    }

    @Test
    void markCodeAsUsed_writesTtlValue() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);

            replayService.markCodeAsUsed("user-b", null, 99L);

            verify(valueOps).set(anyString(), eq("1"), anyLong());
        }
    }
}
