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
package com.richie.component.web.core.hang;

import com.richie.component.web.core.hook.DefaultHookBus;
import com.richie.component.web.core.hook.HookBus;
import com.richie.component.web.core.hook.HookEvent;
import com.richie.component.web.core.protection.LongLivedPathBypass;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HangDetectionInterceptorTest {

    private static ScheduledExecutorService newExec() {
        return Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "test-hang-watch");
            t.setDaemon(true);
            return t;
        });
    }

    @Test
    void normalRequest_cancelsWatchdogAndPublishesNothing() throws Exception {
        ScheduledExecutorService exec = newExec();
        try {
            WatchdogScheduler scheduler = new WatchdogScheduler(exec);
            HookBus hookBus = new DefaultHookBus();
            HangDetectionInterceptor interceptor =
                    new HangDetectionInterceptor(100, true, scheduler, hookBus);

            AtomicReference<HookEvent> received = new AtomicReference<>();
            hookBus.subscribe(HangEvent.class, received::set);

            MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                    .method("GET").path("/api/v1/users").build();
            interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
            Thread.sleep(300);
            assertThat(received.get()).isNull();
            WatchdogScheduler.WatchHandle handle1 = ctx.attribute(HangDetectionInterceptor.WATCH_HANDLE_ATTRIBUTE);
            assertThat(handle1).isNotNull();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void longLivedPath_skipsWatchdog() throws Exception {
        ScheduledExecutorService exec = newExec();
        try {
            WatchdogScheduler scheduler = new WatchdogScheduler(exec);
            HookBus hookBus = new DefaultHookBus();
            HangDetectionInterceptor interceptor =
                    new HangDetectionInterceptor(50, true, scheduler, hookBus);

            AtomicReference<HookEvent> received = new AtomicReference<>();
            hookBus.subscribe(HangEvent.class, received::set);

            MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                    .method("GET").path("/sse/events").build();
            ctx.setAttribute(LongLivedPathBypass.LONG_LIVED_ATTRIBUTE, Boolean.TRUE);
            interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
            Thread.sleep(200);
            assertThat(received.get()).isNull();
            WatchdogScheduler.WatchHandle handle2 = ctx.attribute(HangDetectionInterceptor.WATCH_HANDLE_ATTRIBUTE);
            assertThat(handle2).isNull();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void slowRequest_publishesHangEvent() throws Exception {
        ScheduledExecutorService exec = newExec();
        try {
            WatchdogScheduler scheduler = new WatchdogScheduler(exec);
            HookBus hookBus = new DefaultHookBus();
            HangDetectionInterceptor interceptor =
                    new HangDetectionInterceptor(50, true, scheduler, hookBus);

            AtomicReference<HangEvent> received = new AtomicReference<>();
            hookBus.subscribe(HangEvent.class, received::set);

            MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                    .method("POST").path("/api/v1/upload").build();
            ctx.setClientKey("client-1");
            ctx.setTraceId("trace-slow");
            WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of(
                    (c, ch) -> { try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }));
            interceptor.intercept(ctx, chain);
            await().atMost(1, TimeUnit.SECONDS).until(() -> received.get() != null);
            assertThat(received.get().path()).isEqualTo("/api/v1/upload");
            assertThat(received.get().clientKey()).isEqualTo("client-1");
            assertThat(received.get().traceId()).isEqualTo("trace-slow");
            assertThat(received.get().elapsedMillis()).isGreaterThanOrEqualTo(50L);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void getOrder_is500() {
        ScheduledExecutorService exec = newExec();
        try {
            HangDetectionInterceptor interceptor = new HangDetectionInterceptor(
                    100, true, new WatchdogScheduler(exec), new DefaultHookBus());
            assertThat(interceptor.getOrder()).isEqualTo(HangDetectionInterceptor.ORDER);
            assertThat(HangDetectionInterceptor.ORDER).isEqualTo(500);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void invalidThreshold_throws() {
        ScheduledExecutorService exec = newExec();
        try {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                    new HangDetectionInterceptor(0, true, new WatchdogScheduler(exec), new DefaultHookBus()));
        } finally {
            exec.shutdownNow();
        }
    }
}