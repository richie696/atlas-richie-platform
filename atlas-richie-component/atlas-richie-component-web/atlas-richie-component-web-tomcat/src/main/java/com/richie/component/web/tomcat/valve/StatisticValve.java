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
package com.richie.component.web.tomcat.valve;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tomcat 请求指标采集 Valve（Phase 4）。
 *
 * <p>继承 {@link ValveBase}，在请求处理前后采集 Micrometer 指标：</p>
 * <ul>
 *   <li>{@code {prefix}.requests.total} (Counter) — 累计请求数，tag: {@code method}, {@code status}</li>
 *   <li>{@code {prefix}.request.duration} (Timer) — 请求耗时分布，tag: {@code method}, {@code status}</li>
 *   <li>{@code {prefix}.requests.active} (Gauge) — 当前活跃请求数</li>
 *   <li>{@code {prefix}.requests.errors} (Counter) — 累计 5xx 错误数，tag: {@code method}, {@code status}</li>
 * </ul>
 *
 * <p>Timer 通过 {@code Tags} 缓存避免重复构建 Counter / Timer（MeterRegistry 内部已做幂等，
 * 但 Cache 减少 GC 压力）。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class StatisticValve extends ValveBase {

    private final MeterRegistry meterRegistry;
    private final String prefix;

    private final ConcurrentHashMap<String, Counter> totalCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public StatisticValve(MeterRegistry meterRegistry, String prefix) {
        this.meterRegistry = meterRegistry;
        this.prefix = prefix;
        Gauge.builder(prefix + ".requests.active", activeRequests, AtomicInteger::get)
                .description("Currently active HTTP requests handled by Tomcat")
                .register(meterRegistry);
    }

    @Override
    public void invoke(Request request, Response response)
            throws IOException, jakarta.servlet.ServletException {
        long startNanos = System.nanoTime();
        activeRequests.incrementAndGet();
        boolean handled = false;
        try {
            Valve next = getNext();
            if (next != null) {
                next.invoke(request, response);
            }
            handled = true;
        } finally {
            activeRequests.decrementAndGet();
            long durationNanos = System.nanoTime() - startNanos;
            recordMetrics(request, response, durationNanos, handled);
        }
    }

    private void recordMetrics(Request request, Response response,
                                long durationNanos, boolean handled) {
        try {
            String method = request.getMethod();
            int status = response.getStatus();
            String tagKey = method + ':' + status;

            // Counter: 累计请求数
            totalCounters.computeIfAbsent(tagKey, k -> Counter.builder(prefix + ".requests.total")
                    .description("Total HTTP requests handled by Tomcat")
                    .tags(Tags.of("method", method, "status", String.valueOf(status)))
                    .register(meterRegistry))
                    .increment();

            // Timer: 请求耗时
            timers.computeIfAbsent(tagKey, k -> Timer.builder(prefix + ".request.duration")
                    .description("HTTP request duration distribution")
                    .tags(Tags.of("method", method, "status", String.valueOf(status)))
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .register(meterRegistry))
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            // Counter: 5xx 错误
            if (status >= 500 && status < 600) {
                errorCounters.computeIfAbsent(tagKey, k -> Counter.builder(prefix + ".requests.errors")
                        .description("Total 5xx HTTP errors handled by Tomcat")
                        .tags(Tags.of("method", method, "status", String.valueOf(status)))
                        .register(meterRegistry))
                        .increment();
            }
        } catch (Exception ignored) {
            // metrics must not break the request flow
        }
    }
}
