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

import com.richie.component.cache.function.ZSetFunction;
import com.richie.component.cache.operations.ZSetCapacityLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingOpsImplTest {

    @Mock
    private ZSetFunction fn;

    @InjectMocks
    private RankingOpsImpl ops;

    @Test
    void set_whenHardLimitReached_throws() {
        when(fn.getZSetSize("rank")).thenReturn(ZSetCapacityLimits.ZSET_HARD_MAX_ELEMENTS);

        assertThatThrownBy(() -> ops.set("rank", "u1", 1.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hard capacity limit");
    }

    @Test
    void set_whenUnderLimit_delegates() {
        when(fn.getZSetSize("rank")).thenReturn(0L);

        ops.set("rank", "u1", 99.0);

        verify(fn).addZSetItem("rank", "u1", 99.0);
    }

    @Test
    void setAll_delegates() {
        TreeSet<String> orderSet = new TreeSet<>(Set.of("u1", "u2"));
        ops.setAll("rank", orderSet);
        verify(fn).addZSet("rank", orderSet);
    }

    @Test
    void batchSet_delegates() {
        Map<String, TreeSet<?>> batch = Map.of("rank", new TreeSet<>(Set.of("u1")));
        ops.batchSet(batch);
        verify(fn).batchAddToZSet(batch);
    }

    @Test
    void incrementScore_checksCapacityBeforeWrite() {
        when(fn.getZSetSize("rank")).thenReturn(0L);
        when(fn.incrementScore("rank", "u1", 1.0)).thenReturn(100.0);

        assertThat(ops.incrementScore("rank", "u1", 1.0)).isEqualTo(100.0);
    }

    @Test
    void remove_delegates() {
        ops.remove("rank", "u1", "u2");
        verify(fn).removeZSetItem("rank", "u1", "u2");
    }

    @Test
    void removeByRank_delegates() {
        ops.removeByRank("rank", 0L, 10L);
        verify(fn).removeZSetItem("rank", 0L, 10L);
    }

    @Test
    void removeByScore_delegates() {
        ops.removeByScore("rank", 0.0, 100.0);
        verify(fn).removeZSetItem("rank", 0.0, 100.0);
    }

    @Test
    void popMin_delegates() {
        TypeReference<String> ref = new TypeReference<>() {};
        when(fn.popMinFromZSet("rank", ref)).thenReturn("u1");
        assertThat(ops.popMin("rank", ref)).isEqualTo("u1");
    }

    @Test
    void popMinMultiple_delegates() {
        TypeReference<String> ref = new TypeReference<>() {};
        Set<String> expected = Set.of("u1");
        when(fn.popMinFromZSet("rank", 1L, ref)).thenReturn(expected);
        assertThat(ops.popMin("rank", 1L, ref)).isEqualTo(expected);
    }

    @Test
    void range_delegates() {
        TypeReference<String> ref = new TypeReference<>() {};
        Set<String> expected = Set.of("u1");
        when(fn.reverseRangeWithScores("rank", 0L, 10L, ref)).thenReturn(expected);
        assertThat(ops.range("rank", 0L, 10L, ref)).isEqualTo(expected);
    }

    @Test
    void rangeByScore_delegates() {
        TypeReference<String> ref = new TypeReference<>() {};
        Set<String> expected = Set.of("u1");
        when(fn.reverseRangeByScore("rank", 0.0, 100.0, ref)).thenReturn(expected);
        assertThat(ops.rangeByScore("rank", 0.0, 100.0, ref)).isEqualTo(expected);
    }

    @Test
    void reverseRank_delegates() {
        when(fn.getZSetReverseRank("rank", "u1")).thenReturn(0L);
        assertThat(ops.reverseRank("rank", "u1")).isZero();
    }

    @Test
    void size_delegates() {
        when(fn.getZSetSize("rank")).thenReturn(10L);
        assertThat(ops.size("rank")).isEqualTo(10L);
    }
}
