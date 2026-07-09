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

import com.richie.component.web.core.hook.HookBus;
import com.richie.component.web.core.metrics.WebMetrics;
import com.richie.component.web.core.protection.LongLivedPathBypass;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hang 检测拦截器（README.md §4.4）。
 * <p>
 * 拦截器链最后一位（{@link #ORDER} = 500），注册 watchdog：
 * <ol>
 *   <li>读 ctx 起始纳秒 + 当前业务线程 ref</li>
 *   <li>判 LongLivedPathBypass attribute（§4.8.2 A 组已设）—— 是则跳过 watchdog</li>
 *   <li>注册 3 档 ScheduledFuture（{@code warnMs / dumpMs / killSwitchMs}）；逐档触发时按级联做不同动作</li>
 *   <li>调 chain.proceed；finally cancel 全部 watchdog</li>
 * </ol>
 *
 * <h2>3 档动作分级</h2>
 * <table>
 *   <caption>Hang 检测 3 档动作分级（warn / dump / kill）</caption>
 *   <tr><th>档位</th><th>动作</th></tr>
 *   <tr><td>warn</td><td>log.warn + publish HangEvent(level=WARN) + metrics.hangDetected("warn")</td></tr>
 *   <tr><td>dump</td><td>追加 Thread.getAllStackTraces() 拼字符串入日志 + publish(level=DUMP) + metrics("dump")</td></tr>
 *   <tr><td>kill</td><td>追加 thread dump + {@code threadRef.interrupt()} + publish(level=KILL_SWITCH) + metrics("kill_switch")</td></tr>
 * </table>
 *
 * <h2>背压</h2>
 * <p>同一请求同一档只触发一次——{@link AtomicBoolean#compareAndSet} 守卫；高开销的 dump 仅 dumpMs 触达时跑一次。
 *
 * @author richie696
 * @since 2026-07
 */
public class HangDetectionInterceptor implements WebInterceptor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HangDetectionInterceptor.class);

    public static final int ORDER = 500;

    public static final String WATCH_HANDLE_ATTRIBUTE = "platform.web.hang.watch-handle";

    /**
     * 触发档位（{@code "warn"} / {@code "dump"} / {@code "kill_switch"}）。订阅 {@code HangEvent} 时
     * 可读此 attribute 获取本轮触发的档位（避免改 {@code HangEvent} record 破坏向后兼容）。
     */
    public static final String HANG_LEVEL_ATTRIBUTE = "platform.web.hang.level";

    private final long warnMs;
    private final long dumpMs;
    private final long killSwitchMs;
    private final boolean dumpEnabled;
    private final WatchdogScheduler scheduler;
    private final HookBus hookBus;
    private final WebMetrics metrics;

    public HangDetectionInterceptor(long warnMs, long dumpMs, long killSwitchMs,
                                    boolean dumpEnabled,
                                    WatchdogScheduler scheduler,
                                    HookBus hookBus) {
        this(warnMs, dumpMs, killSwitchMs, dumpEnabled, scheduler, hookBus, WebMetrics.noop());
    }

    /**
     * 旧版 4 参构造器：单一阈值 + dumpEnabled。向后兼容旧测试 / 旧调用方。
     * <p>三档阈值均设为 {@code thresholdMillis}——等价于旧"单档 watchdog"行为。
     */
    public HangDetectionInterceptor(long thresholdMillis, boolean dumpEnabled,
                                    WatchdogScheduler scheduler, HookBus hookBus) {
        this(thresholdMillis, thresholdMillis, thresholdMillis, dumpEnabled,
                scheduler, hookBus, WebMetrics.noop());
    }

    public HangDetectionInterceptor(long warnMs, long dumpMs, long killSwitchMs,
                                    boolean dumpEnabled,
                                    WatchdogScheduler scheduler,
                                    HookBus hookBus,
                                    WebMetrics metrics) {
        if (warnMs <= 0 || dumpMs <= 0 || killSwitchMs <= 0) {
            throw new IllegalArgumentException("thresholds must be > 0");
        }
        if (warnMs > dumpMs || dumpMs > killSwitchMs) {
            throw new IllegalArgumentException(
                    "warnMs(" + warnMs + ") <= dumpMs(" + dumpMs + ") <= killSwitchMs(" + killSwitchMs + ") required");
        }
        this.warnMs = warnMs;
        this.dumpMs = dumpMs;
        this.killSwitchMs = killSwitchMs;
        this.dumpEnabled = dumpEnabled;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.hookBus = Objects.requireNonNull(hookBus, "hookBus must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        Boolean longLived = ctx.attribute(LongLivedPathBypass.LONG_LIVED_ATTRIBUTE);
        if (Boolean.TRUE.equals(longLived)) {
            chain.proceed(ctx);
            return;
        }

        long startNanos = System.nanoTime();
        Thread requestThread = Thread.currentThread();
        AtomicBoolean warnTriggered = new AtomicBoolean(false);
        AtomicBoolean dumpTriggered = new AtomicBoolean(false);
        AtomicBoolean killTriggered = new AtomicBoolean(false);

        WatchdogScheduler.WatchHandle warnHandle = scheduler.schedule(warnMs, () -> {
            if (warnTriggered.compareAndSet(false, true)) {
                onHangLevel(ctx, startNanos, "WARN");
            }
        });
        WatchdogScheduler.WatchHandle dumpHandle = scheduler.schedule(dumpMs, () -> {
            if (dumpTriggered.compareAndSet(false, true)) {
                onHangLevel(ctx, startNanos, "DUMP");
            }
        });
        WatchdogScheduler.WatchHandle killHandle = scheduler.schedule(killSwitchMs, () -> {
            if (killTriggered.compareAndSet(false, true)) {
                onHangLevel(ctx, startNanos, "KILL_SWITCH");
                requestThread.interrupt();
            }
        });
        ctx.setAttribute(WATCH_HANDLE_ATTRIBUTE, warnHandle);

        try {
            chain.proceed(ctx);
        } finally {
            warnHandle.cancel();
            dumpHandle.cancel();
            killHandle.cancel();
        }
    }

    private void onHangLevel(WebRequestContext ctx, long startNanos, String level) {
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
        String threadDump = (dumpEnabled && !"WARN".equals(level)) ? snapshotThreads() : null;
        if (log.isWarnEnabled()) {
            if (threadDump != null) {
                log.warn("Hang detected level={} method={} path={} elapsedMs={} thresholdMs={} clientKey={} traceId={}\n{}",
                        level, ctx.method(), ctx.path(), elapsed, currentThresholdMs(level),
                        ctx.clientKey(), ctx.traceId(), threadDump);
            } else {
                log.warn("Hang detected level={} method={} path={} elapsedMs={} thresholdMs={} clientKey={} traceId={}",
                        level, ctx.method(), ctx.path(), elapsed, currentThresholdMs(level),
                        ctx.clientKey(), ctx.traceId());
            }
        }
        metrics.hangDetected(levelTag(level));
        ctx.setAttribute(HANG_LEVEL_ATTRIBUTE, level);
        hookBus.publish(HangEvent.of(
                ctx.method(),
                ctx.path(),
                elapsed,
                currentThresholdMs(level),
                ctx.clientKey(),
                ctx.traceId()));
    }

    private long currentThresholdMs(String level) {
        return switch (level) {
            case "WARN" -> warnMs;
            case "DUMP" -> dumpMs;
            case "KILL_SWITCH" -> killSwitchMs;
            default -> warnMs;
        };
    }

    private static String levelTag(String level) {
        return switch (level) {
            case "WARN" -> "warn";
            case "DUMP" -> "dump";
            case "KILL_SWITCH" -> "kill_switch";
            default -> level.toLowerCase();
        };
    }

    private static String snapshotThreads() {
        StringBuilder sb = new StringBuilder(2048);
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        sb.append("--- thread dump (").append(all.size()).append(" threads) ---\n");
        for (Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
            Thread t = e.getKey();
            sb.append('"').append(t.getName()).append("\" state=").append(t.getState()).append('\n');
            for (StackTraceElement el : e.getValue()) {
                sb.append("    at ").append(el).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}