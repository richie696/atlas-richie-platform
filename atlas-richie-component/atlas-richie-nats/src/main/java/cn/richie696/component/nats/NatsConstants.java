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
package cn.richie696.component.nats;

/**
 * NATS 组件常量定义
 * <p>
 * 集中维护跨包复用的字符串常量、默认超时与可观测性标识。
 * 设计为 final + 私有构造，禁止实例化或子类化，避免散落的魔法值影响追踪、幂等与日志格式的一致性。
 * </p>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class NatsConstants {

    private NatsConstants() {
    }

    // ===== Header Key 前缀 =====

    /**
     * NATS 组件 Header 命名空间前缀。
     * <p>统一以 {@code nats-} 开头避免与应用层自定义 Header 冲突，便于下游订阅者白名单过滤。</p>
     */
    public static final String HEADER_PREFIX = "nats-";

    /**
     * 消息 ID Header Key，供消费端幂等去重与链路关联使用。
     */
    public static final String HEADER_MESSAGE_ID = HEADER_PREFIX + "message-id";

    /**
     * 追踪 Trace ID Header Key，由生产者注入并在消费端还原到 MDC，确保跨进程调用链可串接。
     */
    public static final String HEADER_TRACE_ID = HEADER_PREFIX + "trace-id";

    /**
     * 消息发送时间戳 Header Key（毫秒），用于端到端延迟诊断。
     */
    public static final String HEADER_SEND_TIME = HEADER_PREFIX + "send-time";

    // ===== 默认超时 =====

    /**
     * 默认 RPC 请求超时（毫秒）。
     * <p>对应 {@code request.default-timeout} 默认值；调用方未指定时长时使用该值兜底。</p>
     */
    public static final long DEFAULT_RPC_TIMEOUT_MS = 5_000L;

    /**
     * 默认去重 TTL（毫秒）。
     * <p>内存/Redis 幂等缓存的过期时间，用于在重投窗口内阻止重复处理。</p>
     */
    public static final long DEFAULT_IDEMPOTENT_TTL_MS = 120_000L;

    /**
     * 默认优雅关闭超时（秒）。
     * <p>容器关闭时等待 drain 完成的兜底时长，超过则强制中断订阅。</p>
     */
    public static final long DEFAULT_DRAIN_TIMEOUT_SECONDS = 30L;

    // ===== 组件标识 =====

    /**
     * 组件追踪器名称，OpenTelemetry {@code TracerProvider} 查找键。
     */
    public static final String TRACER_NAME = "atlas-richie-nats";

    /**
     * 组件追踪器版本，写入 Span 的 instrumentation library 字段，便于按版本筛选可观测数据。
     */
    public static final String TRACER_VERSION = "1.0.0";

    // ===== Redis 去重 Key 前缀 =====

    /**
     * Redis 去重 Key 命名空间前缀。
     * <p>多租户或部署间键空间隔离，避免不同业务流共享 Redis 时相互覆盖。</p>
     */
    public static final String IDEMPOTENT_KEY_PREFIX = "nats:idempotent:";

    // ===== 日志 MDC Key =====

    /**
     * MDC 中 traceId 的字段名，与日志采集模板保持一致，用于日志与追踪关联检索。
     */
    public static final String MDC_TRACE_ID = "traceId";

    /**
     * MDC 中 spanId 的字段名，便于定位单次请求内的具体 span。
     */
    public static final String MDC_SPAN_ID = "spanId";
}
