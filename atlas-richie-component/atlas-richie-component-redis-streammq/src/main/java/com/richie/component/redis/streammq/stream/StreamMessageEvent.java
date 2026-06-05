package com.richie.component.redis.streammq.stream;

import com.richie.component.cache.redis.bean.RecordId;
import lombok.Builder;

/**
 * Redis Stream 消息事件
 *
 * <p>事件总线中分发的标准事件模型，封装了流键、消费者组、消费者、记录ID与消息载荷。
 *
 * @param <T>       事件载荷类型
 * @param streamKey 流键
 * @param group     消费者组
 * @param consumer  消费者名称
 * @param recordId  记录ID
 * @param payload   事件载荷
 */
@Builder
public record StreamMessageEvent<T>(String streamKey, String group, String consumer, RecordId recordId, T payload) {
}


