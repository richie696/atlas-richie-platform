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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 单次/多次累计的纳秒级计时器（Google Guava {@code Stopwatch} 风格，JDK only）。
 * <p>
 * 适用场景：测"一次操作"耗时，可 start/stop/reset 复用，累积 elapsed。
 * 如需聚合多次采样的 count/total/max/mean，请直接使用 Micrometer 的
 * {@code io.micrometer.core.instrument.Timer}（spring-boot-starter-actuator 已传递依赖）。
 *
 * <h2>标准用法</h2>
 * <pre>{@code
 *   // 单次
 *   Stopwatch sw = Stopwatch.createStarted();
 *   doWork();
 *   long ms = sw.stop().elapsed(TimeUnit.MILLISECONDS);
 *
 *   // 多次累计
 *   Stopwatch sw = Stopwatch.createUnstarted();
 *   sw.start(); work(); sw.stop();
 *   sw.start(); work(); sw.stop();
 *   long totalMs = sw.elapsed(TimeUnit.MILLISECONDS);
 * }</pre>
 *
 * <h2>线程安全</h2>
 * <p>本类<strong>非线程安全</strong>：每次任务/线程持独立实例。跨线程共享需外层同步。
 *
 * @author richie696
 * @since 2026-07
 */
public final class Stopwatch {

    private boolean isRunning;
    private long startNanos;
    private long elapsedNanos;

    private Stopwatch() {
    }

    /**
     * 创建未启动实例，后续需手动 {@link #start()}。
     */
    public static Stopwatch createUnstarted() {
        return new Stopwatch();
    }

    /**
     * 创建并立即启动的实例。
     */
    public static Stopwatch createStarted() {
        return new Stopwatch().start();
    }

    /**
     * 启动计时；已运行时抛 {@link IllegalStateException}。
     */
    public Stopwatch start() {
        if (isRunning) {
            throw new IllegalStateException("Stopwatch is already running");
        }
        isRunning = true;
        startNanos = System.nanoTime();
        return this;
    }

    /**
     * 停止计时；未启动时抛 {@link IllegalStateException}。
     * <p>stop 不重置 elapsed，多次 start/stop 周期会<strong>累加</strong>到 elapsed（Guava 风格）。
     */
    public Stopwatch stop() {
        if (!isRunning) {
            throw new IllegalStateException("Stopwatch is not running");
        }
        elapsedNanos += System.nanoTime() - startNanos;
        isRunning = false;
        return this;
    }

    /**
     * 重置为 CREATED 状态：elapsed 清零、isRunning 置 false。
     * <p>无论当前是 running 还是 stopped 均可调用（Guava 行为，不抛异常）。
     */
    public Stopwatch reset() {
        isRunning = false;
        elapsedNanos = 0L;
        startNanos = 0L;
        return this;
    }

    /**
     * 是否正在计时。
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 经过时间（纳秒）。running 时返回"当前累加"，stopped 时返回历史累加。
     */
    public long elapsedNanos() {
        return isRunning
                ? elapsedNanos + (System.nanoTime() - startNanos)
                : elapsedNanos;
    }

    /**
     * 按指定单位返回经过时间。
     *
     * @throws NullPointerException unit 为 null
     */
    public long elapsed(TimeUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        return unit.convert(elapsedNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * 经过时间（{@link Duration} 形式，毫秒/纳秒均可读）。
     */
    public Duration elapsed() {
        return Duration.ofNanos(elapsedNanos());
    }

    /**
     * 人类可读格式（{@code "1.234 ms"} / {@code "5.678 s"} / {@code "1.234 ns"}），用于日志输出。
     */
    @Override
    public String toString() {
        long ns = elapsedNanos();
        if (ns < TimeUnit.MICROSECONDS.toNanos(1)) {
            return ns + " ns";
        }
        if (ns < TimeUnit.SECONDS.toNanos(1)) {
            return ns / (double) TimeUnit.MICROSECONDS.toNanos(1) + " μs";
        }
        if (ns < TimeUnit.MINUTES.toNanos(1)) {
            return ns / (double) TimeUnit.SECONDS.toNanos(1) + " s";
        }
        return ns / (double) TimeUnit.MINUTES.toNanos(1) + " min";
    }
}