package com.richie.component.cache.ops.impl;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.StringFunction;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValueOpsImplTest {

    private static final KeyTypeEnum KT = KeyTypeEnum.STRING;

    @Mock
    private StringFunction fn;

    @Mock
    private L2SyncHelper l2;

    @InjectMocks
    private ValueOpsImpl ops;

    @Test
    void get_delegatesToFunctionThroughL2() {
        OpsTestSupport.passthroughL2Get(l2, KT, "k");
        when(fn.getFromString("k", String.class)).thenReturn("v");

        assertThat(ops.get("k", String.class)).isEqualTo("v");
    }

    @Test
    void get_withTypeReference_delegatesThroughL2() {
        TypeReference<String> ref = new TypeReference<>() {};
        OpsTestSupport.passthroughL2Get(l2, KT, "k");
        when(fn.getFromString("k", ref)).thenReturn("v");

        assertThat(ops.get("k", ref)).isEqualTo("v");
    }

    @Test
    void getMap_delegatesToFunction() {
        TypeReference<String> ref = new TypeReference<>() {};
        Map<String, String> expected = Map.of("a", "1");
        when(fn.getValueMap(List.of("a"), ref)).thenReturn(expected);

        assertThat(ops.getMap(List.of("a"), ref)).isEqualTo(expected);
    }

    @Test
    void getList_delegatesToFunction() {
        TypeReference<String> ref = new TypeReference<>() {};
        List<String> expected = List.of("1", "2");
        when(fn.getObjects(List.of("a", "b"), ref)).thenReturn(expected);

        assertThat(ops.getList(List.of("a", "b"), ref)).isEqualTo(expected);
    }

    @Test
    void set_writesFunctionAndL2() {
        ops.set("k", "v");

        verify(fn).addValue("k", "v");
        verify(l2).put(KT, "k", "v");
    }

    @Test
    void set_withTtl_writesFunctionAndL2() {
        ops.set("k", "v", 60_000L);

        verify(fn).addValue("k", "v", 60_000L);
        verify(l2).put(KT, "k", "v", 60_000L);
    }

    @Test
    void setIfAbsent_whenFalse_skipsL2Put() {
        when(fn.addValueIfAbsent("k", "v")).thenReturn(false);

        assertThat(ops.setIfAbsent("k", "v")).isFalse();
        verify(l2, never()).put(eq(KT), eq("k"), eq("v"));
    }

    @Test
    void setIfAbsent_whenTrue_putsL2() {
        when(fn.addValueIfAbsent("k", "v")).thenReturn(true);

        assertThat(ops.setIfAbsent("k", "v")).isTrue();
        verify(l2).put(KT, "k", "v");
    }

    @Test
    void setIfAbsent_withTtl_whenTrue_putsL2WithExpiry() {
        when(fn.addValueIfAbsent("k", "v", 60_000L)).thenReturn(true);

        assertThat(ops.setIfAbsent("k", "v", 60_000L)).isTrue();
        verify(l2).put(KT, "k", "v", 60_000L);
    }

    @Test
    void increment_updatesL2WithResult() {
        when(fn.increment("c", null)).thenReturn(7L);

        assertThat(ops.increment("c")).isEqualTo(7L);
        verify(l2).put(KT, "c", 7L);
    }

    @Test
    void increment_withDeltaAndTtl_updatesL2() {
        when(fn.increment("c", 2L, 60_000L)).thenReturn(9L);

        assertThat(ops.increment("c", 2L, 60_000L)).isEqualTo(9L);
        verify(l2).put(KT, "c", 9L, 60_000L);
    }

    @Test
    void incrementDouble_withTtl_updatesL2() {
        when(fn.increment("c", 1.5d, 60_000L)).thenReturn(3.5d);

        assertThat(ops.increment("c", 1.5d, 60_000L)).isEqualTo(3.5d);
        verify(l2).put(KT, "c", 3.5d, 60_000L);
    }

    @Test
    void decrement_updatesL2WithResult() {
        when(fn.decrement("c", null)).thenReturn(3L);

        assertThat(ops.decrement("c")).isEqualTo(3L);
        verify(l2).put(KT, "c", 3L);
    }

    @Test
    void decrement_withDeltaAndTtl_updatesL2() {
        when(fn.decrement("c", 2L, 60_000L)).thenReturn(1L);

        assertThat(ops.decrement("c", 2L, 60_000L)).isEqualTo(1L);
        verify(l2).put(KT, "c", 1L, 60_000L);
    }

    @Test
    void set_primitiveTypes_syncL2() {
        ops.set("i", 1);
        ops.set("l", 2L);
        ops.set("f", 1.5f);
        ops.set("d", 2.5d);
        ops.set("b", true);

        verify(fn).addValue("i", 1);
        verify(fn).addValue("l", 2L);
        verify(l2).put(KT, "b", true);
    }

    @Test
    void setIfAbsent_primitives_syncL2WhenTrue() {
        when(fn.addValueIfAbsent("i", 1)).thenReturn(true);
        when(fn.addValueIfAbsent("l", 2L)).thenReturn(false);

        assertThat(ops.setIfAbsent("i", 1)).isTrue();
        assertThat(ops.setIfAbsent("l", 2L)).isFalse();

        verify(l2).put(KT, "i", 1);
        verify(l2, never()).put(eq(KT), eq("l"), eq(2L));
    }

    @Test
    void set_primitivesWithTtl_syncL2() {
        ops.set("i", 1, 60_000L);
        ops.set("l", 2L, 60_000L);
        ops.set("f", 1.5f, 60_000L);
        ops.set("d", 2.5d, 60_000L);
        ops.set("b", true, 60_000L);

        verify(l2).put(KT, "i", 1, 60_000L);
        verify(l2).put(KT, "b", true, 60_000L);
    }

    @Test
    void batchSet_updatesL2ForEachKey() {
        var map = Map.of("a", "1", "b", "2");
        ops.batchSet(map);

        verify(fn).batchAddToString(map);
        verify(l2).put(KT, "a", "1");
        verify(l2).put(KT, "b", "2");
    }

    @Test
    void batchSet_withTtl_updatesL2ForEachKey() {
        var map = Map.of("a", "1");
        ops.batchSet(map, 60_000L);

        verify(fn).batchAddToString(map, 60_000L);
        verify(l2).put(KT, "a", "1", 60_000L);
    }

    @Test
    void batchSetIfAbsent_updatesL2ForEachKey() {
        var map = Map.of("a", "1");
        ops.batchSetIfAbsent(map);

        verify(fn).batchUpdateIfAbsent(map, null);
        verify(l2).put(KT, "a", "1");
    }

    @Test
    void batchSetIfAbsent_withTtl_updatesL2ForEachKey() {
        var map = Map.of("a", "1");
        ops.batchSetIfAbsent(map, 60_000L);

        verify(fn).batchUpdateIfAbsent(map, 60_000L);
        verify(l2).put(KT, "a", "1", 60_000L);
    }

    @Test
    void getWithLock_delegatesToL2() {
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "cfg");
        when(fn.getFromStringWithLock(eq("cfg"), any(), eq(60_000L))).thenReturn("loaded");

        assertThat(ops.getWithLock("cfg", 60_000L, () -> "db")).isEqualTo("loaded");
    }
}
