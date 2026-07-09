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

import com.richie.component.cache.function.LockFunction;
import com.richie.component.cache.redis.manage.CacheBatchLock;
import com.richie.component.cache.redis.manage.CacheLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockOpsImplTest {

    @Mock
    private LockFunction fn;

    @InjectMocks
    private LockOpsImpl ops;

    @Test
    void optimisticWithRenewal_delegatesToFunction() {
        CacheLock lock = new CacheLock(true, "r");
        when(fn.lockWithRenewal("k", 5L, true)).thenReturn(lock);

        assertThat(ops.optimisticWithRenewal("k", 5L)).isSameAs(lock);
    }

    @Test
    void batch_allSuccess_returnsBatchWithLocks() {
        CacheLock lock1 = new CacheLock(true, "r1");
        CacheLock lock2 = new CacheLock(true, "r2");
        when(fn.optimisticLock("k1", 20L)).thenReturn(lock1);
        when(fn.optimisticLock("k2", 20L)).thenReturn(lock2);

        CacheBatchLock batch = ops.batch(List.of("k1", "k2"), 20L, TimeUnit.SECONDS);

        assertThat(batch.isSuccess()).isTrue();
    }

    @Test
    void batch_partialFailure_rollsBackAndReturnsEmpty() {
        CacheLock lock1 = mock(CacheLock.class);
        CacheLock lock2 = mock(CacheLock.class);
        when(lock1.isSuccess()).thenReturn(true);
        when(lock2.isSuccess()).thenReturn(false);
        when(fn.optimisticLock("k1", 20L)).thenReturn(lock1);
        when(fn.optimisticLock("k2", 20L)).thenReturn(lock2);

        CacheBatchLock batch = ops.batch(List.of("k1", "k2"), 20L, TimeUnit.SECONDS);

        assertThat(batch.isSuccess()).isFalse();
        verify(lock1).close();
    }

    @Test
    void optimistic_withDefaultSeconds_delegates() {
        CacheLock lock = new CacheLock(true, "r");
        when(fn.optimisticLock("k", 3L)).thenReturn(lock);

        assertThat(ops.optimistic("k")).isSameAs(lock);
    }

    @Test
    void optimistic_withCustomSeconds_delegates() {
        CacheLock lock = new CacheLock(true, "r");
        when(fn.optimisticLock("k", 10L)).thenReturn(lock);

        assertThat(ops.optimistic("k", 10L)).isSameAs(lock);
    }

    @Test
    void pessimistic_delegates() {
        CacheLock lock = new CacheLock(true, "r");
        when(fn.pessimisticLock("k", 3L)).thenReturn(lock);

        assertThat(ops.pessimistic("k")).isSameAs(lock);
    }

    @Test
    void pessimisticWithRenewal_delegates() {
        CacheLock lock = new CacheLock(true, "r");
        when(fn.lockWithRenewal("k", 10L, false)).thenReturn(lock);

        assertThat(ops.pessimisticWithRenewal("k", 10L)).isSameAs(lock);
    }
}
