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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldOpsImplTest {

    private static final KeyTypeEnum KT = KeyTypeEnum.HASH;

    @Mock
    private HashFunction fn;

    @Mock
    private L2SyncHelper l2;

    @InjectMocks
    private FieldOpsImpl ops;

    @Test
    void set_delegatesToHashFunction() {
        ops.set("u:1", "name", "Tom");

        verify(fn).addHash("u:1", "name", "Tom");
    }

    @Test
    void get_delegatesThroughL2() {
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "u:1:name");
        when(fn.getFromHash("u:1", "name", String.class)).thenReturn("Tom");

        assertThat(ops.get("u:1", "name", String.class)).isEqualTo("Tom");
    }

    @Test
    void get_withTypeReference_delegatesThroughL2() {
        TypeReference<String> ref = new TypeReference<>() {};
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "u:1:name");
        when(fn.getFromHash("u:1", "name", ref)).thenReturn("Tom");

        assertThat(ops.get("u:1", "name", ref)).isEqualTo("Tom");
    }

    @Test
    void exists_delegatesToFunction() {
        when(fn.existsInHash("u:1", "name")).thenReturn(true);

        assertThat(ops.exists("u:1", "name")).isTrue();
    }

    @Test
    void setAll_registersTypeAndWritesL2() {
        Map<String, String> map = Map.of("name", "Tom");

        ops.setAll("u:1", map, 60_000L);

        verify(fn).addHash("u:1", map);
        verify(l2).registerType("u:1", Map.class);
        verify(l2).put(KT, "u:1", map, 60_000L);
    }

    @Test
    void getAll_delegatesThroughL2() {
        OpsTestSupport.passthroughL2Get(l2, KT, "u:1");
        Map<String, String> expected = Map.of("name", "Tom");
        when(fn.getAllMapFromHash("u:1", String.class)).thenReturn(expected);

        assertThat(ops.getAll("u:1", String.class)).isEqualTo(expected);
    }

    @Test
    void get_multiFields_delegatesToFunction() {
        TypeReference<String> ref = new TypeReference<>() {};
        List<String> expected = List.of("Tom");
        when(fn.getFromHash("u:1", List.of("name"), ref)).thenReturn(expected);

        assertThat(ops.get("u:1", List.of("name"), ref)).isEqualTo(expected);
    }

    @Test
    void getFields_delegatesToFunction() {
        when(fn.getHashKeyList("u:1")).thenReturn(Set.of("name"));

        assertThat(ops.getFields("u:1")).containsExactly("name");
    }

    @Test
    void size_delegatesToFunction() {
        when(fn.getHashSize("u:1")).thenReturn(2L);

        assertThat(ops.size("u:1")).isEqualTo(2L);
    }

    @Test
    void remove_delegatesToFunction() {
        ops.remove("u:1", "name", "age");

        verify(fn).removeHashItem("u:1", "name", "age");
    }

    @Test
    void batchSet_delegatesToFunction() {
        Map<String, Map<String, ?>> batch = Map.of("u:1", Map.of("name", "Tom"));

        ops.batchSet(batch);

        verify(fn).batchAddToHash(batch);
    }

    @Test
    void getWithLock_withClass_delegatesThroughL2() {
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "u:1:name");
        when(fn.getFromHashWithLock(eq("u:1"), eq("name"), eq(String.class), any(), eq(60_000L)))
                .thenReturn("Tom");

        assertThat(ops.getWithLock("u:1", "name", String.class, 60_000L, () -> "db")).isEqualTo("Tom");
    }

    @Test
    void getWithLock_withTypeReference_delegatesThroughL2() {
        TypeReference<String> ref = new TypeReference<>() {};
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "u:1:name");
        when(fn.getFromHashWithLock(eq("u:1"), eq("name"), eq(ref), any(), eq(60_000L)))
                .thenReturn("Tom");

        assertThat(ops.getWithLock("u:1", "name", ref, 60_000L, () -> "db")).isEqualTo("Tom");
    }
}
