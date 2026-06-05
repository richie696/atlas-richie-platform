package com.richie.component.redis.streammq.stream;

import reactor.core.publisher.Sinks;

/**
 * 事件发布结果封装类
 *
 * <p>这是一个不可变的记录类，用于封装 Redis Stream 事件总线中事件发布操作的结果信息。
 * 它提供了成功和失败两种状态的统一表示，并包含详细的错误信息用于���题诊断和处理。
 *
 * <p><strong>设计特点：</strong>
 * <ul>
 *   <li><strong>不可变性</strong>：基于 record 实现，确保结果对象创建后不可修改</li>
 *   <li><strong>类型安全</strong>：使用泛型参数，支持任意类型的事件对象</li>
 *   <li><strong>详细信息</strong>：包含成功标志、原始事件、失败原因和异常信息</li>
 *   <li><strong>工厂方法</strong>：提供静态工厂方法，简化对象创建过程</li>
 *   <li><strong>转换支持</strong>：支持从 Reactor Sinks.EmitResult 自动转换</li>
 * </ul>
 *
 * <p><strong>核心功能：</strong>
 * <ul>
 *   <li><strong>结果封装</strong>：统一封装发布操作的成功和失败状态</li>
 *   <li><strong>错误诊断</strong>：提供详细的失败原因和异常信息</li>
 *   <li><strong>事件追踪</strong>：保留原始事件对象，便于后续处理</li>
 *   <li><strong>类型转换</strong>：支持 Reactor 框架结果到业务结果的转换</li>
 * </ul>
 *
 * <p><strong>使用场景：</strong>
 * <ul>
 *   <li><strong>发布确认</strong>：确认事件是否成功发布到事件总线</li>
 *   <li><strong>错误处理</strong>：根据失败原因执行相应的错误处理逻辑</li>
 *   <li><strong>监控统计</strong>：统计发布成功率和失败原因分布</li>
 *   <li><strong>重试机制</strong>：根据失败类型决定是否重试发布</li>
 *   <li><strong>日志记录</strong>：记录发布结果用于审计和问题排查</li>
 * </ul>
 *
 * <p><strong>使用示例：</strong>
 * <pre>{@code
 * // 处理发布结果
 * PublishResult<OrderEvent> result = eventBus.publishOrderEvent(orderEvent);
 * if (result.success()) {
 *     log.info("订单事件发布成功: {}", result.event().getOrderId());
 * } else {
 *     switch (result.failureReason()) {
 *         case BUFFER_OVERFLOW -> {
 *             log.warn("事件缓冲区溢出，稍后重试");
 *             scheduleRetry(result.event());
 *         }
 *         case SINK_TERMINATED -> {
 *             log.error("事件总线已终止，需要重新初始化");
 *             reinitializeEventBus();
 *         }
 *         case EXCEPTION -> {
 *             log.error("发布异常: {}", result.error().getMessage(), result.error());
 *         }
 *         default -> {
 *             log.error("未知发布失败: {}", result.failureReason());
 *         }
 *     }
 * }
 *
 * // 批量处理结果
 * List<PublishResult<OrderEvent>> results = publishBatchEvents(events);
 * long successCount = results.stream().filter(PublishResult::success).count();
 * long failureCount = results.size() - successCount;
 * log.info("批量发布完成: 成功={}, 失败={}", successCount, failureCount);
 * }</pre>
 *
 * <p><strong>失败原因说明：</strong>
 * <ul>
 *   <li><strong>SINK_TERMINATED</strong>：事件发布器已终止，无法继续发布</li>
 *   <li><strong>BUFFER_OVERFLOW</strong>：缓冲区已满，背压机制触发</li>
 *   <li><strong>OPERATION_CANCELLED</strong>：发布操作被取消</li>
 *   <li><strong>NON_SERIALIZED_ACCESS</strong>：并发访问冲突</li>
 *   <li><strong>UNKNOWN_ERROR</strong>：未知的发布错误</li>
 *   <li><strong>EXCEPTION</strong>：发布过程中抛出异常</li>
 * </ul>
 *
 * @param <T> 事件对象的类型参数
 * @param success 发布是否成功的标志位
 * @param event 被发布的原始事件对象，用于后续处理或重试
 * @param failureReason 发布失败的具体原因，成功时为 null
 * @param error 发布过程中的异常对象，无异常时为 null
 *
 * @author richie696
 * @since 2025-09-15
 * @see PublishFailureReason 发布失败原因枚举
 * @see RedisStreamEventBus Redis Stream 事件总线
 * @see reactor.core.publisher.Sinks.EmitResult Reactor 发布结果枚举
 */
public record PublishResult<T>(boolean success, T event, PublishFailureReason failureReason, Throwable error) {

    /**
     * 创建成功的发布结果
     *
     * <p>用于表示事件成功发布到事件总线的情况。
     * 成功结果中 failureReason 和 error 字段均为 null。
     *
     * @param <T> 事件对象的类型参数
     * @param event 成功发布的事件对象
     * @return 表示成功的 PublishResult 实例
     *
     * @see #failed(Object, PublishFailureReason) 创建失败结果
     */
    public static <T> PublishResult<T> success(T event) {
        return new PublishResult<>(true, event, null, null);
    }

    /**
     * 创建失败的发布结果（无异常信息）
     *
     * <p>用于表示事件发布失败但没有具体异常信息的情况。
     * 通常用于 Reactor 框架返回的标准失败状态。
     *
     * @param <T> 事件对象的类型参数
     * @param event 发布失败的事件对象，可用于重试或日志记录
     * @param reason 发布失败的具体原因
     * @return 表示失败的 PublishResult 实例
     *
     * @see #failed(Object, PublishFailureReason, Throwable) 创建带异常信息的失败结果
     */
    public static <T> PublishResult<T> failed(T event, PublishFailureReason reason) {
        return new PublishResult<>(false, event, reason, null);
    }

    /**
     * 创建失败的发布结果（包含异常信息）
     *
     * <p>用于表示事件发布过程中抛出异常的情况。
     * 包含详细的异常信息，便于问题诊断和处理。
     *
     * @param <T> 事件对象的类型参数
     * @param event 发布失败的事件对象，可用于重试或日志记录
     * @param reason 发布失败的具体原因，通常为 EXCEPTION
     * @param error 发布过程中抛出的异常对象
     * @return 表示失败的 PublishResult 实例
     *
     * @see #failed(Object, PublishFailureReason) 创建无异常信息的失败结果
     */
    public static <T> PublishResult<T> failed(T event, PublishFailureReason reason, Throwable error) {
        return new PublishResult<>(false, event, reason, error);
    }

    /**
     * 从 Reactor Sinks.EmitResult 转换为 PublishResult（包内可见）
     *
     * <p>将 Reactor 框架的发布结果转换为业务层的发布结果。
     * 这个方法仅供包内的事件总线实现类使用，提供了统一的结果转换逻辑��
     *
     * <p><strong>转换映射：</strong>
     * <ul>
     *   <li><strong>OK</strong> → 成功结果</li>
     *   <li><strong>FAIL_TERMINATED</strong> → SINK_TERMINATED 失败</li>
     *   <li><strong>FAIL_OVERFLOW</strong> → BUFFER_OVERFLOW 失败</li>
     *   <li><strong>FAIL_CANCELLED</strong> → OPERATION_CANCELLED 失败</li>
     *   <li><strong>FAIL_NON_SERIALIZED</strong> → NON_SERIALIZED_ACCESS 失败</li>
     *   <li><strong>其他</strong> → UNKNOWN_ERROR 失败</li>
     * </ul>
     *
     * @param <T> 事件对象的类型参数
     * @param result Reactor 框架的发布结果
     * @param event 相关的事件对象
     * @param name 事件类型名称，用于日志和调试（当前版本未使用）
     * @return 转换后的 PublishResult 实例
     *
     * @see reactor.core.publisher.Sinks.EmitResult Reactor 发布结果枚举
     */
    static <T> PublishResult<T> fromEmit(Sinks.EmitResult result, T event, String name) {
        return switch (result) {
            case OK -> PublishResult.success(event);
            case FAIL_TERMINATED -> PublishResult.failed(event, PublishFailureReason.SINK_TERMINATED);
            case FAIL_OVERFLOW -> PublishResult.failed(event, PublishFailureReason.BUFFER_OVERFLOW);
            case FAIL_CANCELLED -> PublishResult.failed(event, PublishFailureReason.OPERATION_CANCELLED);
            case FAIL_NON_SERIALIZED -> PublishResult.failed(event, PublishFailureReason.NON_SERIALIZED_ACCESS);
            default -> PublishResult.failed(event, PublishFailureReason.UNKNOWN_ERROR);
        };
    }
}
