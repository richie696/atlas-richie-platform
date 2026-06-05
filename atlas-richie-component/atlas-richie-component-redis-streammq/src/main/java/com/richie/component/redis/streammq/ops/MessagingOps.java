package com.richie.component.redis.streammq.ops;

/**
 * 发布订阅（Pub/Sub）通知操作接口。
 * <p>提供基于 Redis Pub/Sub 的通知能力，用于轻量级消息发布。</p>
 */
public interface MessagingOps {

    Long publish(String topic, Object message);
}
