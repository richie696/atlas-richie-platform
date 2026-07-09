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
package com.richie.component.web.jetty.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Jetty 模块扩展配置属性。
 *
 * <p>前缀：{@code platform.component.web.jetty}。</p>
 *
 * <h2>本模块提供的独家能力（其余全部依赖 Spring Boot 标准配置）</h2>
 * <ul>
 *   <li>{@link TraceId} — Trace ID 注入（比 Spring Interceptor 更早介入）</li>
 *   <li>{@link AccessLog} — 结构化 JSON Access Log（替换 Jetty 默认 NCSA）</li>
 *   <li>{@link Metrics} — 自定义 Micrometer 指标前缀</li>
 * </ul>
 *
 * <h2>已交给 Spring Boot 标准配置（不在本模块）</h2>
 * <ul>
 *   <li>线程池调优 → {@code server.jetty.threads.*}（含 max/min/idle-timeout/max-queue-capacity/selectors/acceptors）</li>
 *   <li>Connector 配置 → {@code server.port} + {@code server.jetty.*}</li>
 *   <li>HTTP/2 → {@code server.http2.enabled}</li>
 *   <li>优雅停服 → {@code server.shutdown=graceful} + {@code spring.lifecycle.timeout-per-shutdown-phase}</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.jetty")
public class JettyProperties {

    @NestedConfigurationProperty
    private AccessLog accessLog = new AccessLog();

    @NestedConfigurationProperty
    private TraceId traceId = new TraceId();

    @NestedConfigurationProperty
    private Metrics metrics = new Metrics();

    @Data
    public static class AccessLog {
        private boolean enabled = false;
        private String format = "json";
        private String directory = "/var/log/richie/jetty";
        private String filenamePrefix = "access";
        private String filenameDateFormat = "yyyy_MM_dd";
    }

    @Data
    public static class TraceId {
        private boolean enabled = true;
        private String header = "X-Trace-Id";
        private boolean generateIfMissing = true;
    }

    @Data
    public static class Metrics {
        private boolean enabled = true;
        private String prefix = "atlas_jetty";
    }
}
