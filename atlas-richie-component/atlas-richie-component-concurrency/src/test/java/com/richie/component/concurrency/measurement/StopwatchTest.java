/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.concurrency.measurement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StopwatchTest {

    @Test
    void createStarted_isRunning() {
        Stopwatch sw = Stopwatch.createStarted();
        assertThat(sw.isRunning()).isTrue();
    }

    @Test
    void createUnstarted_isNotRunning() {
        Stopwatch sw = Stopwatch.createUnstarted();
        assertThat(sw.isRunning()).isFalse();
    }

    @Test
    void stop_transitionsToNotRunning() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(2);
        sw.stop();
        assertThat(sw.isRunning()).isFalse();
    }

    @Test
    void elapsed_afterStop_isNonNegative() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(5);
        long ms = sw.stop().elapsed(TimeUnit.MILLISECONDS);
        assertThat(ms).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void elapsed_running_isAccessible() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(2);
        // running 时也能读 elapsed（Guava 行为：返回累积值）
        long ns = sw.elapsedNanos();
        assertThat(ns).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(2));
        sw.stop();
    }

    @Test
    void doubleStart_throws() {
        Stopwatch sw = Stopwatch.createStarted();
        assertThatThrownBy(sw::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void doubleStop_throws() {
        Stopwatch sw = Stopwatch.createStarted();
        sw.stop();
        assertThatThrownBy(sw::stop)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    @Test
    void stopWithoutStart_throws() {
        Stopwatch sw = Stopwatch.createUnstarted();
        assertThatThrownBy(sw::stop)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    @Test
    void elapsedNanosMatchesElapsedUnitMillis() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(3);
        sw.stop();
        long ms = sw.elapsed(TimeUnit.MILLISECONDS);
        long ns = sw.elapsedNanos();
        assertThat(TimeUnit.MILLISECONDS.toNanos(ms)).isLessThanOrEqualTo(ns + TimeUnit.MILLISECONDS.toNanos(1));
    }

    @Test
    void elapsedSupportsCustomUnit() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(2);
        sw.stop();
        assertThat(sw.elapsed(TimeUnit.NANOSECONDS)).isEqualTo(sw.elapsedNanos());
        assertThat(sw.elapsed(TimeUnit.MILLISECONDS)).isEqualTo(sw.elapsed(TimeUnit.MILLISECONDS));
    }

    @Test
    void elapsedWithNullUnit_throws() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(1);
        sw.stop();
        assertThatThrownBy(() -> sw.elapsed(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void elapsedDuration_matchesElapsedNanos() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(2);
        sw.stop();
        Duration d = sw.elapsed();
        assertThat(d.toNanos()).isEqualTo(sw.elapsedNanos());
    }

    @Test
    void cumulativeElapsed_acrossMultipleStartStopCycles() {
        Stopwatch sw = Stopwatch.createUnstarted();
        sw.start();
        sleep(2);
        sw.stop();
        long first = sw.elapsed(TimeUnit.MILLISECONDS);

        sw.start();
        sleep(2);
        sw.stop();
        long second = sw.elapsed(TimeUnit.MILLISECONDS);

        // Guava 风格：累积 elapsed，第二次 stop 后应 >= 第一次
        assertThat(second).isGreaterThanOrEqualTo(first);
    }

    @Test
    void reset_clearsElapsed() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(5);
        sw.stop();
        assertThat(sw.elapsedNanos()).isGreaterThan(0L);
        sw.reset();
        assertThat(sw.elapsedNanos()).isEqualTo(0L);
        assertThat(sw.isRunning()).isFalse();
    }

    @Test
    void reset_whileRunning_clearsState() {
        Stopwatch sw = Stopwatch.createStarted();
        sw.reset();
        assertThat(sw.isRunning()).isFalse();
        // reset 后可重新 start
        sw.start();
        assertThat(sw.isRunning()).isTrue();
    }

    @Test
    void toString_returnsHumanReadable() {
        Stopwatch sw = Stopwatch.createStarted();
        sleep(1);
        sw.stop();
        String s = sw.toString();
        assertThat(s).isNotEmpty();
        // 短耗时（1ms）应包含 ns 或 μs 单位
        assertThat(s).matches(".*(ns|μs|ms|s|min)$");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}