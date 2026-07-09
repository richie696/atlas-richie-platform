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
package com.richie.component.web.core.hang;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WatchdogSchedulerTest {

    @Test
    void schedule_firesAfterThreshold() {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "test-watchdog");
            t.setDaemon(true);
            return t;
        });
        try {
            WatchdogScheduler scheduler = new WatchdogScheduler(exec);
            AtomicInteger count = new AtomicInteger();
            WatchdogScheduler.WatchHandle handle = scheduler.schedule(50, count::incrementAndGet);
            await().atMost(1, TimeUnit.SECONDS).until(() -> count.get() == 1);
            assertThat(handle.isCancelled()).isFalse();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void cancel_preventsFiring() {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "test-watchdog");
            t.setDaemon(true);
            return t;
        });
        try {
            WatchdogScheduler scheduler = new WatchdogScheduler(exec);
            AtomicInteger count = new AtomicInteger();
            WatchdogScheduler.WatchHandle handle = scheduler.schedule(200, count::incrementAndGet);
            boolean cancelled = handle.cancel();
            assertThat(cancelled).isTrue();
            Thread.sleep(400);
            assertThat(count.get()).isZero();
            assertThat(handle.isCancelled()).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void cancel_twice_returnsFalseSecond() {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "test-watchdog");
            t.setDaemon(true);
            return t;
        });
        try {
            WatchdogScheduler scheduler = new WatchdogScheduler(exec);
            WatchdogScheduler.WatchHandle handle = scheduler.schedule(10_000, () -> {});
            assertThat(handle.cancel()).isTrue();
            assertThat(handle.cancel()).isFalse();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void getDefault_returnsSingleton() {
        WatchdogScheduler a = WatchdogScheduler.getDefault();
        WatchdogScheduler b = WatchdogScheduler.getDefault();
        assertThat(a).isSameAs(b);
    }
}