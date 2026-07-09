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
package com.richie.component.redis.streammq.config.tracing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis Stream 链路追踪配置属性
 *
 * <p>用于配置 Redis Stream 链路追踪相关的各种参数，包括导出器、采样、属性等。
 *
 * <p><strong>配置示例：</strong>
 * <pre>{@code
 * platform:
 *   cache:
 *     redis:
 *       stream:
 *         tracing:
 *           enabled: true
 *           service-name: redis-stream-service
 *           service-version: 1.0.0
 *           sampling:
 *             probability: 1.0
 *           otlp:
 *             enabled: true
 *             endpoint: http://localhost:4317
 * }</pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15 16:45:10
 */
@Data
@ConfigurationProperties(prefix = "platform.cache.redis.stream.tracing")
public class RedisStreamTracingProperties {

    /**
     * 是否启用链路追踪
     */
    private boolean enabled = false;

    /**
     * 服务名称
     */
    private String serviceName = "redis-stream-service";

    /**
     * 服务版本
     */
    private String serviceVersion = "1.0.0";

    /**
     * 采样配置
     */
    private Sampling sampling = new Sampling();

    /**
     * 追踪属性配置
     */
    private Attributes attributes = new Attributes();

    /**
     * 追踪事件配置
     */
    private Events events = new Events();

    /**
     * 错误追踪配置
     */
    private ErrorTracing errorTracing = new ErrorTracing();

    /**
     * OTLP 配置
     */
    private Otlp otlp = new Otlp();

    /**
     * Zipkin 配置
     */
    private Zipkin zipkin = new Zipkin();

    /**
     * 日志配置
     */
    private Logging logging = new Logging();

    /**
     * 采样配置
     */
    @Data
    public static class Sampling {
        /**
         * 采样率 (0.0-1.0)
         */
        private double probability = 1.0;

        /**
         * 是否启用自适应采样
         */
        private boolean adaptive = false;
    }

    /**
     * 追踪属性配置
     */
    @Data
    public static class Attributes {
        /**
         * 是否记录消息内容
         */
        private boolean recordMessageContent = false;

        /**
         * 是否记录消息元数据
         */
        private boolean recordMessageMetadata = true;

        /**
         * 是否记录处理时间
         */
        private boolean recordProcessingTime = true;

        /**
         * 是否记录错误详情
         */
        private boolean recordErrorDetails = true;
    }

    /**
     * 追踪事件配置
     */
    @Data
    public static class Events {
        /**
         * 是否记录消息接收事件
         */
        private boolean recordMessageReceived = true;

        /**
         * 是否记录消息处理开始事件
         */
        private boolean recordProcessingStarted = true;

        /**
         * 是否记录消息处理完成事件
         */
        private boolean recordProcessingCompleted = true;

        /**
         * 是否记录消息确认事件
         */
        private boolean recordMessageAcknowledged = true;

        /**
         * 是否记录重试事件
         */
        private boolean recordRetryEvents = true;
    }

    /**
     * 错误追踪配置
     */
    @Data
    public static class ErrorTracing {
        /**
         * 是否记录错误堆栈
         */
        private boolean recordStackTrace = false;

        /**
         * 是否记录错误上下文
         */
        private boolean recordErrorContext = true;

        /**
         * 最大错误消息长度
         */
        private int maxErrorMessageLength = 1000;
    }

    /**
     * OTLP 配置
     *
     * <p>OTLP 是 OpenTelemetry 的标准协议，支持多种后端系统：
     * <ul>
     *   <li>Jaeger (通过 OTLP 接收器)</li>
     *   <li>Zipkin (通过 OTLP 接收器)</li>
     *   <li>Elastic APM</li>
     *   <li>New Relic</li>
     *   <li>DataDog</li>
     * </ul>
     */
    @Data
    public static class Otlp {
        /**
         * 是否启用 OTLP 导出器
         */
        private boolean enabled = false;

        /**
         * OTLP 端点
         */
        private String endpoint = "http://localhost:4317";

        /**
         * 服务名称
         */
        private String serviceName = "redis-stream-service";

        /**
         * 协议类型 (grpc 或 http)
         */
        private String protocol = "http";

        /**
         * 超时时间（秒）
         */
        private int timeoutSeconds = 30;
    }

    /**
     * Zipkin 配置
     */
    @Data
    public static class Zipkin {
        /**
         * 是否启用 Zipkin 导出器
         */
        private boolean enabled = false;

        /**
         * Zipkin 端点
         */
        private String endpoint = "http://localhost:9411/api/v2/spans";

        /**
         * 服务名称
         */
        private String serviceName = "redis-stream-service";
    }

    /**
     * 日志配置
     */
    @Data
    public static class Logging {
        /**
         * 是否启用日志导出器
         */
        private boolean enabled = true;

        /**
         * 日志级别
         */
        private String level = "INFO";
    }
}
