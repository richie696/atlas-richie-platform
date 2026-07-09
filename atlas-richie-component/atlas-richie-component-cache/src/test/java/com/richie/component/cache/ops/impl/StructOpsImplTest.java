/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.ops.impl;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.HashFunction;
import com.richie.component.cache.ops.L2SyncHelper;
import com.richie.component.cache.support.OpsTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructOpsImplTest {

    private static final KeyTypeEnum KT = KeyTypeEnum.HASH;

    @Mock
    private HashFunction fn;

    @Mock
    private L2SyncHelper l2;

    @InjectMocks
    private StructOpsImpl ops;

    @Test
    void get_delegatesThroughL2() {
        OpsTestSupport.passthroughL2Get(l2, KT, "obj");
        when(fn.getObjectFromHash("obj", String.class)).thenReturn("v");

        assertThat(ops.get("obj", String.class)).isEqualTo("v");
    }

    @Test
    void get_withTypeReference_delegatesThroughL2() {
        TypeReference<String> ref = new TypeReference<>() {};
        OpsTestSupport.passthroughL2Get(l2, KT, "obj");
        when(fn.getObjectFromHash("obj", ref)).thenReturn("v");

        assertThat(ops.get("obj", ref)).isEqualTo("v");
    }

    @Test
    void set_withoutTtl_writesL2() {
        ops.set("obj", "payload");

        verify(fn).addObject("obj", "payload");
        verify(l2).put(KT, "obj", "payload");
    }

    @Test
    void set_withTtl_writesL2WithExpiry() {
        ops.set("obj", "payload", 60_000L);

        verify(fn).addObject("obj", "payload", 60_000L);
        verify(l2).put(KT, "obj", "payload", 60_000L);
    }

    @Test
    void refresh_updatesL2() {
        when(fn.refreshObject(eq("obj"), any())).thenReturn("new");

        String result = ops.refresh("obj", v -> "new");
        assertThat(result).isEqualTo("new");
        verify(l2).put(KT, "obj", "new");
    }

    @Test
    void getWithLock_withClass_delegatesThroughL2() {
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "obj");
        when(fn.getObjectFromHashWithLock(eq("obj"), eq(String.class), any(), eq(60_000L)))
                .thenReturn("loaded");

        assertThat(ops.getWithLock("obj", String.class, 60_000L, () -> "db")).isEqualTo("loaded");
    }

    @Test
    void getWithLock_withTypeReference_delegatesThroughL2() {
        TypeReference<String> ref = new TypeReference<>() {};
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "obj");
        when(fn.getObjectFromHashWithLock(eq("obj"), eq(ref), any(), eq(60_000L)))
                .thenReturn("loaded");

        assertThat(ops.getWithLock("obj", ref, 60_000L, () -> "db")).isEqualTo("loaded");
    }
}
