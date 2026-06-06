package com.richie.component.logging.handler;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.logging.domain.OperatorInfo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperatorContextHolderTest {

    @Test
    void operatorLifecycle_usesGlobalCache() {
        KeyOps keyOps = mock(KeyOps.class);
        StructOps structOps = mock(StructOps.class);
        OperatorInfo info = new OperatorInfo("u1", "Alice");

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(keyOps.hasKey(anyString())).thenReturn(true);
            when(structOps.get(anyString(), eq(OperatorInfo.class))).thenReturn(info);

            OperatorContextHolder.setOperator("token", "u1", "Alice", 60_000L);

            assertThat(OperatorContextHolder.hasOperator("token")).isTrue();
            assertThat(OperatorContextHolder.getOperator("token")).isEqualTo(info);
            verify(structOps).set(startsWith("platform:operator:"), eq(info), eq(60_000L));
        }
    }
}
