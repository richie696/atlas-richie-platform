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
package com.richie.component.web.jetty.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Jetty 12 请求指标采集 Handler（Phase 4）。
 *
 * <p>基于 Micrometer 采集 HTTP 请求级指标，指标前缀由 {@code platform.component.web.jetty.metrics.prefix} 配置
 * （默认 {@code atlas_jetty}）。</p>
 *
 * <p>采集指标：</p>
 * <ul>
 *   <li>{@code {prefix}.requests.total} — 请求总数 Counter（Tag: method, status）</li>
 *   <li>{@code {prefix}.request.duration} — 请求延迟 Timer（Tag: method, status，P50/P95/P99）</li>
 *   <li>{@code {prefix}.requests.active} — 活跃请求数 Gauge</li>
 *   <li>{@code {prefix}.requests.errors} — 5xx 错误数 Counter（Tag: method, status）</li>
 * </ul>
 *
 * <p>指标命名和标记策略与项目内 {@code GrpcServerMetricsInterceptor} 一致。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class StatisticHandler extends Handler.Wrapper {

    private static final Logger log = LoggerFactory.getLogger(StatisticHandler.class);

    private final MeterRegistry registry;
    private final String prefix;
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public StatisticHandler(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = (prefix == null || prefix.isBlank()) ? "atlas_jetty" : prefix;

        Gauge.builder(this.prefix + ".requests.active", activeRequests, AtomicInteger::get)
                .description("Active HTTP requests")
                .register(registry);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        activeRequests.incrementAndGet();
        Timer.Sample sample = Timer.start(registry);
        try {
            return super.handle(request, response, callback);
        } finally {
            try {
                String method = request.getMethod();
                int status = response.getStatus();
                String statusStr = String.valueOf(status);

                sample.stop(Timer.builder(prefix + ".request.duration")
                        .tag("method", method)
                        .tag("status", statusStr)
                        .description("HTTP request duration")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry));

                Counter.builder(prefix + ".requests.total")
                        .tag("method", method)
                        .tag("status", statusStr)
                        .description("Total HTTP requests")
                        .register(registry)
                        .increment();

                if (status >= 500) {
                    Counter.builder(prefix + ".requests.errors")
                            .tag("method", method)
                            .tag("status", statusStr)
                            .description("HTTP 5xx error responses")
                            .register(registry)
                            .increment();
                }
            } catch (Exception e) {
                log.warn("Failed to record Jetty metrics: method={}", request.getMethod(), e);
            } finally {
                activeRequests.decrementAndGet();
            }
        }
    }
}
