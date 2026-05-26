package com.richie.component.cache.redis.bean;

/**
 * Redis Stream 消息记录
 *
 * <p>表示 Redis Stream 中的一条消息，包含流名、消息ID与消息体。
 *
 * @param <T>    消息体类型
 * @param stream 流名称
 * @param id     消息ID
 * @param body   消息体
 */
public record StreamMessage<T>(
        String stream,
        RecordId id,
        T body
) {
}
