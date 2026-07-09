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
package com.richie.component.redis.streammq.config.stream;

import com.richie.contract.model.BaseStreamMessage;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Stream 消费者配置属性
 *
 * <p>支持通过 YAML 配置文件定义多个消费者的配置，配合 {@code @RedisStreamConsumer} 注解按名称绑定。
 *
 * <p>配置示例：
 * <pre>{@code
 * platform:
 *   cache:
 *     redis:
 *       stream:
 *         consumers:
 *           enabled: true
 *           configs:
 *             user-events:
 *               stream-key: "user-events"
 *               group: "user-processors"
 *               consumer: "user-consumer-1"
 *               target-type: "com.richie.component.cache.domain.UserInfo"
 *               auto-ack: true
 *               concurrency: 2
 *               error-strategy: RETRY
 *               max-retries: 3
 *               retry-delay: 1s
 *               idempotency-enabled: true
 * }</pre>
 *
 * @author richie696
 * @since 2025-12-09
 */
@Data
@ConfigurationProperties(prefix = "platform.cache.redis.stream.consumers")
public class RedisStreamProperties {

    /**
     * 是否启用消费者自动配置（为true时，通过配置文件自动加载消费者类，为false时，通过消费者构造函数初始化）
     */
    private boolean enabled = true;

    /**
     * 消费者配置映射
     * key: 配置名称（对应 @RedisStreamConsumer 注解的 value）
     * value: 具体配置
     */
    private Map<String, ConsumerConfig> configs = new HashMap<>();

    /**
     * Stream 清理服务配置
     */
    private CleanupConfig cleanup = new CleanupConfig();

    /**
     * Stream 清理服务配置
     */
    @Data
    @ConfigurationProperties(prefix = "platform.cache.redis.stream.consumers.cleanup")
    public static class CleanupConfig {

        /**
         * 清理间隔
         * <p>
         * 默认1小时（PT1H）
         * 
         * <p>
         * 支持 Spring Boot Duration 格式，例如：
         * 
         * <ul>
         *   <li>{@code 1h} - 1小时</li>
         *   <li>{@code 30m} - 30分钟</li>
         *   <li>{@code PT1H} - ISO-8601 格式，1小时</li>
         *   <li>{@code 3600000} - 毫秒数（不推荐，建议使用可读格式）</li>
         * </ul>
         */
        private Duration interval = Duration.ofHours(1);

        /**
         * Stream 最大保留消息数（全局默认值）
         * <p>
         * 用于自动清理已消费的消息，这是全局默认值。
         * 如果某个消费者配置（{@code ConsumerConfig.maxLen}）没有设置或为 0，
         * 则使用此全局默认值进行清理。
         * 
         * <ul>
         *   <li>设置为 0 或负数：不限制，不清理（默认）</li>
         *   <li>设置为正数：保留最新的 maxLen 条消息，自动清理旧消息</li>
         *   <li>建议值：根据业务需求和消息量设置，例如 10000、50000 等</li>
         * </ul>
         * <p>
         * <strong>优先级：</strong>
         * 
         * <ol>
         *   <li>消费者配置的 {@code maxLen}（如果设置了且 > 0）</li>
         *   <li>全局默认值 {@code cleanup.defaultMaxLen}（如果设置了且 > 0）</li>
         *   <li>不清理（如果都未设置或为 0）</li>
         * </ol>
         */
        private Long defaultMaxLen = 3000L;
    }

    /**
     * 单个消费者配置
     */
    @Data
    public static class ConsumerConfig {

        /**
         * Stream 键名
         */
        private String streamKey;

        /**
         * 消费者组名
         */
        private String group;

        /**
         * 消费者名称
         */
        private String consumer = "default-consumer";

        /**
         * 目标类型（消息负载类型）
         * 对于死信队列，如果未指定则默认为 DeadLetterMessage
         */
        private Class<? extends BaseStreamMessage> targetType;

        /**
         * 是否自动确认消息
         */
        private boolean autoAck = true;

        /**
         * 并发处理数
         */
        private int concurrency = 1;

        /**
         * 错误处理策略
         */
        private ErrorStrategy errorStrategy = ErrorStrategy.SKIP;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 重试延迟
         */
        private Duration retryDelay = Duration.ofSeconds(1);

        /**
         * 是否启用幂等去重
         */
        private boolean idempotencyEnabled = true;

        /**
         * 是否自动启动
         */
        private boolean autoStart = true;

        /**
         * 单次拉取消息数量
         * <p>
         * 每次从 Redis Stream 拉取消息时，一次拉取的消息条数。
         * 默认值为 1，表示每次只拉取 1 条消息。
         * 可以根据消息处理能力和网络延迟情况调整，建议值：
         * <ul>
         *   <li>消息量大、处理速度快：可以设置为 10-50</li>
         *   <li>消息量小、处理速度慢：保持默认值 1</li>
         *   <li>高并发场景：可以设置为 5-20</li>
         * </ul>
         * 
         */
        private int count = 1;

        /**
         * Stream 最大保留消息数（用于自动清理已消费的消息）
         * <p>
         * Redis Stream 类似于 Kafka，消息被 ACK 后不会自动删除，需要通过 XTRIM 命令清理。
         * 
         * <ul>
         *   <li>设置为 0 或负数：不限制，不清理（默认）</li>
         *   <li>设置为正数：保留最新的 maxLen 条消息，自动清理旧消息</li>
         *   <li>建议值：根据业务需求和消息量设置，例如 10000、50000 等</li>
         * </ul>
         * <p>
         * <strong>注意：</strong>清理操作使用分布式锁确保多实例环境下只有一个实例执行。
         * 
         */
        private Long maxLen = 0L;
    }

    /**
     * 错误处理策略枚举
     */
    public enum ErrorStrategy {
        /**
         * 跳过错误消息并确认
         */
        SKIP,
        /**
         * 重试一次处理
         */
        RETRY,
        /**
         * 不确认消息，留待后续处理
         */
        NO_ACK
    }
}
