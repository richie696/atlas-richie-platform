package com.richie.component.cache.redis.utils;

import com.richie.component.cache.GlobalCache;
import com.richie.contract.model.BaseStreamMessage;
import com.richie.component.cache.redis.stream.EventContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 死信队列工具类
 *
 * <p>提供统一的死信队列发送能力，支持多种死信队列策略，并封装通用的死信消息结构。
 *
 * <p>主要功能：
 * <ul>
 *   <li>根据策略（全局/按类型/按源流/混合）发送到不同的 DLQ</li>
 *   <li>构建标准化的死信消息，包含上下文、错误类型与堆栈</li>
 *   <li>提供带业务ID/优先级的重载便捷方法</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * &#64;Autowired
 * private DeadLetterQueueUtil deadLetterQueueUtil;
 *
 * &#64;Override
 * protected void onError(Throwable e, UserInfo userInfo, EventContext ctx) {
 *     deadLetterQueueUtil.sendToDeadLetterQueue(userInfo, e, ctx, this.getClass());
 * }
 * }</pre>
 *
 * @author richie696
 * @version 5.0.0
 * @since 2025-12-09
 */
@Slf4j
@Component
public class DeadLetterQueueUtil {

    /**
     * 死信队列策略枚举
     */
    public enum DeadLetterStrategy {
        /**
         * 全局死信队列：所有消息发送到同一个死信队列
         */
        GLOBAL,

        /**
         * 按消息类型分组：相同类型的消息发送到同一个死信队列
         */
        BY_MESSAGE_TYPE,

        /**
         * 按源队列分组：相同源队列的消息发送到同一个死信队列
         */
        BY_SOURCE_STREAM,

        /**
         * 混合模式：同时发送到多个死信队列
         */
        HYBRID
    }

    /**
     * 发送消息到死信队列（使用默认策略）
     *
     * @param originalMessage 原始消息
     * @param error 异常信息
     * @param ctx 事件上下文
     * @param sourceConsumer 来源消费者类
     */
    public static void sendToDeadLetterQueue(Object originalMessage, Throwable error, EventContext ctx, Class<?> sourceConsumer) {
        sendToDeadLetterQueue(originalMessage, error, ctx, sourceConsumer, DeadLetterStrategy.GLOBAL);
    }

    /**
     * 发送消息到死信队列（指定策略）
     *
     * @param originalMessage 原始消息
     * @param error 异常信息
     * @param ctx 事件上下文
     * @param sourceConsumer 来源消费者类
     * @param strategy 死信队列策略
     */
    public static void sendToDeadLetterQueue(Object originalMessage, Throwable error, EventContext ctx,
                                    Class<?> sourceConsumer, DeadLetterStrategy strategy) {
        try {
            // 构建死信队列消息
            DeadLetterMessage deadLetterMessage = buildDeadLetterMessage(originalMessage, error, ctx, sourceConsumer);

            // 根据策略发送到不同的死信队列
            sendToDeadLetterQueues(deadLetterMessage, ctx.streamKey(), strategy);

            log.info("死信队列消息发送成功: messageType={}, strategy={}, streamKey={}, group={}, recordId={}",
                    originalMessage.getClass().getSimpleName(), strategy, ctx.streamKey(), ctx.group(), ctx.recordId());

        } catch (Exception e) {
            log.error("发送到死信队列失败: messageType={}, error={}",
                    originalMessage.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * 构建死信队列消息
     *
     * @param originalMessage 原始消息
     * @param error           异常
     * @param ctx             事件上下文
     * @param sourceConsumer  来源消费者类
     * @return 死信消息对象
     */
    private static DeadLetterMessage buildDeadLetterMessage(Object originalMessage, Throwable error,
                                                   EventContext ctx, Class<?> sourceConsumer) {
        return DeadLetterMessage.of(originalMessage, error, ctx, sourceConsumer.getSimpleName());
    }

    /**
     * 根据策略发送到死信队列
     *
     * @param deadLetterMessage 死信消息
     * @param originalStreamKey 原始 Stream 键
     * @param strategy          发送策略
     */
    private static void sendToDeadLetterQueues(DeadLetterMessage deadLetterMessage, String originalStreamKey,
                                      DeadLetterStrategy strategy) {
        switch (strategy) {
            case GLOBAL:
                sendToDeadLetterQueue(deadLetterMessage, "dlq:global");
                break;

            case BY_MESSAGE_TYPE:
                String messageType = deadLetterMessage.originalMessageType();
                String typeName = messageType.substring(messageType.lastIndexOf('.') + 1);
                sendToDeadLetterQueue(deadLetterMessage, "dlq:type:%s".formatted(typeName));
                break;

            case BY_SOURCE_STREAM:
                sendToDeadLetterQueue(deadLetterMessage, "dlq:stream:%s".formatted(originalStreamKey));
                break;

            case HYBRID:
                // 同时发送到多个死信队列
                sendToDeadLetterQueue(deadLetterMessage, "dlq:global");
                String typeName2 = deadLetterMessage.originalMessageType();
                String simpleTypeName = typeName2.substring(typeName2.lastIndexOf('.') + 1);
                sendToDeadLetterQueue(deadLetterMessage, "dlq:type:%s".formatted(simpleTypeName));
                sendToDeadLetterQueue(deadLetterMessage, "dlq:stream:%s".formatted(originalStreamKey));
                break;
        }
    }

    /**
     * 发送到指定死信队列
     *
     * @param deadLetterMessage  死信消息
     * @param deadLetterStreamKey 死信队列 Stream 键
     */
    private static void sendToDeadLetterQueue(DeadLetterMessage deadLetterMessage, String deadLetterStreamKey) {
        try {
            String recordId = GlobalCache.stream().publish(deadLetterStreamKey, deadLetterMessage);
            log.debug("死信队列消息发送成功: deadLetterStreamKey={}, recordId={}", deadLetterStreamKey, recordId);
        } catch (Exception e) {
            log.error("发送到死信队列失败: deadLetterStreamKey={}, error={}", deadLetterStreamKey, e.getMessage(), e);
        }
    }

    /**
     * 获取异常堆栈信息
     *
     * @param error 异常对象
     * @return 堆栈字符串
     */
    private static String getStackTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 通用死信队列消息封装类
     *
     * <p>使用 record 类实现，提供不可变的数据传输对象
     * <p>自动生成构造函数、getter、equals、hashCode、toString 方法
     *
     * @param originalMessage 原始消息（使用Object类型，支持任意消息类型）
     * @param originalMessageType 原始消息类型，用于反序列化
     * @param originalStreamKey 原始Stream键名
     * @param originalGroup 原始消费者组
     * @param originalRecordId 原始记录ID
     * @param errorMessage 错误消息
     * @param errorType 错误类型
     * @param stackTrace 异常堆栈信息
     * @param timestamp 时间戳
     * @param sourceConsumer 来源消费者
     * @param retryCount 重试次数
     * @param businessId 业务ID，如订单号、用户ID等
     * @param priority 优先级：HIGH, MEDIUM, LOW
     */
    public record DeadLetterMessage(
            Object originalMessage,
            String originalMessageType,
            String originalStreamKey,
            String originalGroup,
            String originalRecordId,
            String errorMessage,
            String errorType,
            String stackTrace,
            Long timestamp,
            String sourceConsumer,
            Integer retryCount,
            String businessId,
            String priority
    ) implements BaseStreamMessage {

        /**
         * 创建死信队列消息的便捷方法
         *
         * @param originalMessage 原始消息
         * @param error 异常对象
         * @param ctx 事件上下文
         * @param sourceConsumer 来源消费者
         * @return 死信队列消息
         */
        public static DeadLetterMessage of(Object originalMessage, Throwable error, EventContext ctx, String sourceConsumer) {
            return new DeadLetterMessage(
                    originalMessage,
                    originalMessage.getClass().getName(),
                    ctx.streamKey(),
                    ctx.group(),
                    ctx.recordId().value(),
                    error.getMessage(),
                    error.getClass().getSimpleName(),
                    getStackTrace(error),
                    System.currentTimeMillis(),
                    sourceConsumer,
                    0,
                    null,
                    null
            );
        }

        /**
         * 创建带业务ID的死信队列消息
         *
         * @param originalMessage 原始消息
         * @param error 异常对象
         * @param ctx 事件上下文
         * @param sourceConsumer 来源消费者
         * @param businessId 业务ID
         * @param priority 优先级
         * @return 死信队列消息
         */
        public static DeadLetterMessage of(Object originalMessage, Throwable error, EventContext ctx,
                                         String sourceConsumer, String businessId, String priority) {
            return new DeadLetterMessage(
                    originalMessage,
                    originalMessage.getClass().getName(),
                    ctx.streamKey(),
                    ctx.group(),
                    ctx.recordId().value(),
                    error.getMessage(),
                    error.getClass().getSimpleName(),
                    getStackTrace(error),
                    System.currentTimeMillis(),
                    sourceConsumer,
                    0,
                    businessId,
                    priority
            );
        }

        /**
         * 获取异常堆栈信息
         *
         * @param error 异常对象
         * @return 堆栈字符串
         */
        private static String getStackTrace(Throwable error) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            error.printStackTrace(pw);
            return sw.toString();
        }
    }
}
