package com.richie.component.cache.redis.stream;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.function.StreamFunction;
import com.richie.component.cache.redis.bean.RecordId;

/**
 * Redis Stream 消息事件上下文
 *
 * <p>这是一个不可变的记录类，封装了处理 Redis Stream 消息时所需的核心信息。
 * 它作为消息处理器与底层 Redis Stream 操作之间的桥梁，提供了消息确认等关键功能。
 *
 * <p>主要用途：
 * <ul>
 *   <li>封装消息处理所需的上下文信息</li>
 *   <li>提供消息确认(ACK)操作</li>
 *   <li>维护 Stream 键、消费者组和记录ID的关联关系</li>
 *   <li>支持消息处理的事务性操作</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 在消息处理器中使用
 * public void handle(OrderEvent payload, EventContext ctx) {
 *     try {
 *         // 处理业务逻辑
 *         processOrder(payload);
 *
 *         // 手动确认消息处理成功
 *         ctx.ack();
 *     } catch (Exception e) {
 *         // 处理失败，不调用 ack()，消息将保留在 Stream 中等待重试
 *         log.error("处理订单消息失败", e);
 *     }
 * }
 * }</pre>
 *
 * @param streamKey Redis Stream 的键名，标识具体的消息流
 * @param group 消费者组名称，用于支持多消费者并行处理
 * @param recordId 消息记录的唯一标识符，用于消息确认和重复处理检测
 *
 * @author richie696
 * @since 2025-09-15
 * @see StreamFunction
 * @see RecordId
 * @see AbstractStreamConsumer
 */
public record EventContext(String streamKey, String group, RecordId recordId) {

    /**
     * 静态内部类，用于持有私有实例
     */
    private static class ContextHolder {

        /** Stream 操作函数（由框架注入） */
        private static StreamFunction streamFunction;
        
        static void setStreamFunction(StreamFunction stream) {
            streamFunction = stream;
        }
        
        static StreamFunction getStreamFunction() {
            return streamFunction;
        }
    }

    /**
     * 设置 StreamFunction 实例（由框架内部调用）
     *
     * @param streamFunction StreamFunction 实例
     */
    public static void setStreamFunction(StreamFunction streamFunction) {
        ContextHolder.setStreamFunction(streamFunction);
    }

    /**
     * 确认消息处理完成
     *
     * <p>向 Redis Stream 发送 ACK 确认，表示当前消息已被成功处理。
     * 确认后的消息将从消费者组的待处理列表(Pending List)中移除。
     *
     * <p>注意事项：
     * <ul>
     *   <li>只有在消息处理完全成功后才应该调用此方法</li>
     *   <li>如果处理失败不调用此方法，消息将保留在待处理列表中</li>
     *   <li>未确认的消息可以被重新分配给其他消费者处理</li>
     *   <li>重复调用此方法是安全的，Redis 会忽略重复的确认</li>
     * </ul>
     *
     * <p>使用场景：
     * <ul>
     *   <li>业务逻辑处理成功后的确认</li>
     *   <li>数据持久化完成后的确认</li>
     *   <li>外部系统调用成功后的确认</li>
     * </ul>
     *
     * @throws RuntimeException 如果确认操作失败（如网络异常、Redis 服务不可用等）
     */
    public void ack() {
        // 优先使用静态持有的 StreamFunction 实例，fallback 到 GlobalCache
        StreamFunction stream = ContextHolder.getStreamFunction();
        if (stream == null) {
            stream = GlobalCache.stream();
        }
        if (stream == null) {
            throw new IllegalStateException("StreamFunction 实例不可用，无法确认消息");
        }
        stream.acknowledge(streamKey, group, recordId.value());
    }
}
