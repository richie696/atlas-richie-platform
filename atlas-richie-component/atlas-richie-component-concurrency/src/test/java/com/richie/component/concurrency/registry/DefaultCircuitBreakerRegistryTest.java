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
package com.richie.component.concurrency.registry;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCircuitBreakerRegistryTest {

    private final DefaultCircuitBreakerRegistry registry = new DefaultCircuitBreakerRegistry();

    @AfterEach
    void cleanup() {
        registry.clear();
    }

    @Test
    void getOrCreate_sameKey_returnsSameInstance() {
        CircuitBreaker first = registry.getOrCreate("client-a", k -> CircuitBreaker.ofDefaults());
        CircuitBreaker second = registry.getOrCreate("client-a", k -> CircuitBreaker.builder()
                .failurePercent(99).openDuration(Duration.ofMinutes(5)).build());

        assertThat(first).isSameAs(second);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void getOrCreate_differentKeys_returnsDifferentInstances() {
        CircuitBreaker a = registry.getOrCreate("client-a", k -> CircuitBreaker.ofDefaults());
        CircuitBreaker b = registry.getOrCreate("client-b", k -> CircuitBreaker.ofDefaults());

        assertThat(a).isNotSameAs(b);
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.keys()).containsExactlyInAnyOrder("client-a", "client-b");
    }

    @Test
    void find_returnsEmpty_whenKeyAbsent() {
        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void find_returnsInstance_whenKeyPresent() {
        CircuitBreaker created = registry.getOrCreate("k", k -> CircuitBreaker.ofDefaults());
        Optional<CircuitBreaker> found = registry.find("k");

        assertThat(found).containsSame(created);
    }

    @Test
    void remove_returnsInstance_andEvictsFromCache() {
        CircuitBreaker created = registry.getOrCreate("k", k -> CircuitBreaker.ofDefaults());
        Optional<CircuitBreaker> removed = registry.remove("k");

        assertThat(removed).containsSame(created);
        assertThat(registry.find("k")).isEmpty();
        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    void remove_returnsEmpty_whenKeyAbsent() {
        assertThat(registry.remove("missing")).isEmpty();
    }

    @Test
    void clear_emptiesCache() {
        registry.getOrCreate("a", k -> CircuitBreaker.ofDefaults());
        registry.getOrCreate("b", k -> CircuitBreaker.ofDefaults());
        assertThat(registry.size()).isEqualTo(2);

        registry.clear();

        assertThat(registry.size()).isEqualTo(0);
        assertThat(registry.keys()).isEmpty();
    }

    @Test
    void concurrent_getOrCreate_invokesFactoryOncePerKey() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicReference<CircuitBreaker> firstSeen = new AtomicReference<>();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    CircuitBreaker cb = registry.getOrCreate("hot-key", k -> {
                        factoryCalls.incrementAndGet();
                        return CircuitBreaker.ofDefaults();
                    });
                    firstSeen.compareAndSet(null, cb);
                    assertThat(cb).isSameAs(firstSeen.get());
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(factoryCalls).hasValue(1);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void keys_returnsImmutableSnapshot() {
        registry.getOrCreate("a", k -> CircuitBreaker.ofDefaults());
        registry.getOrCreate("b", k -> CircuitBreaker.ofDefaults());

        Set<String> snapshot = registry.keys();

        assertThat(snapshot).containsExactlyInAnyOrder("a", "b");
        assertThatThrownBy(() -> snapshot.add("c"));
    }

    private static void assertThatThrownBy(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }
}