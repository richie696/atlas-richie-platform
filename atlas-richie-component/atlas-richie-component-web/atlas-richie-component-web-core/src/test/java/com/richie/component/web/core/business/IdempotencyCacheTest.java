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
package com.richie.component.web.core.business;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class IdempotencyCacheTest {

    @Test
    void firstPut_returnsTrue() {
        IdempotencyCache cache = new IdempotencyCache(60);
        assertThat(cache.putIfAbsent("k1")).isTrue();
    }

    @Test
    void secondPutWithinTtl_returnsFalse() {
        IdempotencyCache cache = new IdempotencyCache(60);
        cache.putIfAbsent("k1");
        assertThat(cache.putIfAbsent("k1")).isFalse();
    }

    @Test
    void putAfterTtl_returnsTrue() throws InterruptedException {
        IdempotencyCache cache = new IdempotencyCache(1);
        cache.putIfAbsent("k1");
        Thread.sleep(1100);
        assertThat(cache.putIfAbsent("k1")).isTrue();
    }

    @Test
    void differentKeys_independent() {
        IdempotencyCache cache = new IdempotencyCache(60);
        cache.putIfAbsent("k1");
        assertThat(cache.putIfAbsent("k2")).isTrue();
    }

    @Test
    void nullKey_throwsNpe() {
        IdempotencyCache cache = new IdempotencyCache(60);
        assertThatNullPointerException().isThrownBy(() -> cache.putIfAbsent(null));
    }

    @Test
    void size_excludesExpired() throws InterruptedException {
        IdempotencyCache cache = new IdempotencyCache(1);
        cache.putIfAbsent("k1");
        cache.putIfAbsent("k2");
        assertThat(cache.size()).isEqualTo(2);
        Thread.sleep(1100);
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void invalidTtl_throws() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new IdempotencyCache(0));
    }
}