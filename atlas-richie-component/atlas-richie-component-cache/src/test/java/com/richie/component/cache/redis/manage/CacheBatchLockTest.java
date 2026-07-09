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
package com.richie.component.cache.redis.manage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CacheBatchLockTest {

    @Test
    void isSuccess_falseWhenEmpty() {
        assertThat(new CacheBatchLock(Set.of()).isSuccess()).isFalse();
    }

    @Test
    void isSuccess_trueWhenNonEmpty() {
        CacheLock lock = new CacheLock(true, "req");
        assertThat(new CacheBatchLock(Set.of(lock)).isSuccess()).isTrue();
    }

    @Test
    void close_releasesAllLocks() {
        CacheLock lock1 = mock(CacheLock.class);
        CacheLock lock2 = mock(CacheLock.class);
        try (var batch = new CacheBatchLock(Set.of(lock1, lock2))) {
            assertThat(batch.isSuccess()).isTrue();
        }
        verify(lock1).close();
        verify(lock2).close();
    }
}
