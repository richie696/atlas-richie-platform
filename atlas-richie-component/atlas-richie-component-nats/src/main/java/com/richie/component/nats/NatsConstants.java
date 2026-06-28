package com.richie.component.nats;

/**
 * NATS 组件常量定义
 *
 * @author richie696
 * @since 1.0.0
 */
public final class NatsConstants {

    private NatsConstants() {
    }

    // ===== Header Key 前缀 =====

    /** NATS 组件 Header 命名空间前缀 */
    public static final String HEADER_PREFIX = "nats-";

    /** 消息 ID Header Key */
    public static final String HEADER_MESSAGE_ID = HEADER_PREFIX + "message-id";

    /** 追踪 Trace ID Header Key */
    public static final String HEADER_TRACE_ID = HEADER_PREFIX + "trace-id";

    /** 消息发送时间戳 Header Key */
    public static final String HEADER_SEND_TIME = HEADER_PREFIX + "send-time";

    // ===== 默认超时 =====

    /** 默认 RPC 请求超时（毫秒） */
    public static final long DEFAULT_RPC_TIMEOUT_MS = 5_000L;

    /** 默认去重 TTL（毫秒） */
    public static final long DEFAULT_IDEMPOTENT_TTL_MS = 120_000L;

    /** 默认优雅关闭超时（秒） */
    public static final long DEFAULT_DRAIN_TIMEOUT_SECONDS = 30L;

    // ===== 组件标识 =====

    /** 组件追踪器名称 */
    public static final String TRACER_NAME = "atlas-richie-nats";

    /** 组件追踪器版本 */
    public static final String TRACER_VERSION = "1.0.0";

    // ===== Redis 去重 Key 前缀 =====

    /** Redis 去重 Key 前缀 */
    public static final String IDEMPOTENT_KEY_PREFIX = "nats:idempotent:";

    // ===== 日志 MDC Key =====

    /** MDC traceId Key */
    public static final String MDC_TRACE_ID = "traceId";

    /** MDC spanId Key */
    public static final String MDC_SPAN_ID = "spanId";
}
