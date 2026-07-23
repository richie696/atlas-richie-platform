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
package com.richie.component.vector.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BatchStatsTest {

    @Test
    void empty_returnsAllZeros() {
        BatchStats stats = BatchStats.empty();

        assertThat(stats.total()).isZero();
        assertThat(stats.succeeded()).isZero();
        assertThat(stats.failed()).isZero();
        assertThat(stats.elapsed()).isEqualTo(Duration.ZERO);
        assertThat(stats.embeddingApiCalls()).isZero();
        assertThat(stats.writeApiCalls()).isZero();
    }

    @Test
    void totalsAreSumOfSucceededAndFailed() {
        BatchStats stats = new BatchStats(10, 7, 3, Duration.ofMillis(500), 4, 2);

        assertThat(stats.total()).isEqualTo(10L);
        assertThat(stats.succeeded() + stats.failed()).isEqualTo(stats.total());
    }

    @Test
    void allAccessorsExposeConstructorArgs() {
        Duration elapsed = Duration.ofSeconds(2);
        BatchStats stats = new BatchStats(5, 4, 1, elapsed, 3, 2);

        assertThat(stats.total()).isEqualTo(5L);
        assertThat(stats.succeeded()).isEqualTo(4L);
        assertThat(stats.failed()).isEqualTo(1L);
        assertThat(stats.elapsed()).isEqualTo(elapsed);
        assertThat(stats.embeddingApiCalls()).isEqualTo(3L);
        assertThat(stats.writeApiCalls()).isEqualTo(2L);
    }
}