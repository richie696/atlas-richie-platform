package com.richie.component.cache.ops.impl;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.SetFunction;
import com.richie.component.cache.ops.L2SyncHelper;
import com.richie.component.cache.operations.SetCapacityLimits;
import com.richie.component.cache.support.OpsTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionOpsImplTest {

    private static final KeyTypeEnum KT = KeyTypeEnum.SET;

    @Mock
    private SetFunction fn;

    @Mock
    private L2SyncHelper l2;

    @InjectMocks
    private CollectionOpsImpl ops;

    @Test
    void get_delegatesThroughL2() {
        OpsTestSupport.passthroughL2Get(l2, KT, "tags");
        Set<String> expected = Set.of("java");
        when(fn.getFromSet("tags", String.class)).thenReturn(expected);

        assertThat(ops.get("tags", String.class)).isEqualTo(expected);
    }

    @Test
    void set_writesFunctionAndL2() {
        Set<String> values = Set.of("java", "go");

        ops.set("tags", values, 60_000L);

        verify(fn).addSet("tags", values);
        verify(l2).put(KT, "tags", values, 60_000L);
    }

    @Test
    void add_whenHardLimitReached_throws() {
        when(fn.getSetSize("tags")).thenReturn(SetCapacityLimits.SET_HARD_MAX_ELEMENTS);

        assertThatThrownBy(() -> ops.add("tags", "java"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hard capacity limit");
    }

    @Test
    void add_whenUnderLimit_delegatesToFunction() {
        when(fn.getSetSize("tags")).thenReturn(0L);
        when(l2.isEnabled(KT)).thenReturn(false);

        ops.add("tags", "java");

        verify(fn).addSetItem("tags", "java");
    }

    @Test
    void add_whenL2Enabled_putsValueToL2() {
        when(fn.getSetSize("tags")).thenReturn(0L);
        when(l2.isEnabled(KT)).thenReturn(true);

        ops.add("tags", "java");

        verify(l2).put(KT, "tags", "java");
    }

    @Test
    void size_delegates() {
        when(fn.getSetSize("tags")).thenReturn(3L);
        assertThat(ops.size("tags")).isEqualTo(3L);
    }

    @Test
    void exists_delegates() {
        when(fn.existsInSet("tags", "java")).thenReturn(true);
        assertThat(ops.exists("tags", "java")).isTrue();
    }

    @Test
    void remove_delegates() {
        ops.remove("tags", "java", "go");
        verify(fn).removeSetItem("tags", "java", "go");
    }

    @Test
    void batchSet_delegates() {
        Map<String, Set<?>> batch = Map.of("tags", Set.of("java"));
        ops.batchSet(batch);
        verify(fn).batchAddToSet(batch);
    }

    @Test
    void pop_delegates() {
        when(fn.popDataFromSet("tags", String.class)).thenReturn("java");
        assertThat(ops.pop("tags", String.class)).isEqualTo("java");
    }

    @Test
    void popMultiple_delegates() {
        Set<String> expected = Set.of("java", "go");
        when(fn.popMembersFromSet("tags", 2L, String.class)).thenReturn(expected);
        assertThat(ops.pop("tags", 2L, String.class)).isEqualTo(expected);
    }

    @Test
    void getWithLock_delegatesThroughL2() {
        OpsTestSupport.passthroughL2GetWithLock(l2, KT, "tags");
        Set<String> expected = Set.of("java");
        when(fn.getFromSetWithLock(eq("tags"), eq(String.class), any(), eq(60_000L))).thenReturn(expected);

        assertThat(ops.getWithLock("tags", String.class, 60_000L, () -> expected)).isEqualTo(expected);
    }
}
