package com.richie.component.nats.strategy;

/**
 * NATS 消息幂等去重策略接口
 *
 * <p>基于消息 ID 进行去重检查，防止重复消费。
 * 支持 Redis（多实例部署）和 Memory（单实例部署）两种实现。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsIdempotentChecker {

    /**
     * 检查消息是否为首次处理
     *
     * @param messageId 消息唯一标识
     * @param ttlMillis 去重 TTL（毫秒）
     * @return true=首次处理，false=重复消息
     */
    boolean isFirstTime(String messageId, long ttlMillis);

    /**
     * 清除消息去重记录（处理失败后调用，允许重试）
     *
     * @param messageId 消息唯一标识
     */
    void clear(String messageId);
}
