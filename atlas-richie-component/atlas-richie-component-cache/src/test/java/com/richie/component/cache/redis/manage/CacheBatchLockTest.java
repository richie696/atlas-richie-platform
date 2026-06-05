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
