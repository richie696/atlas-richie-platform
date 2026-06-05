package com.richie.component.cache.ops;

/**
 * 发布订阅（Pub/Sub）通知操作接口。
 * <p>提供基于 Redis Pub/Sub 的通知能力，用于轻量级消息发布。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface NotificationOps {

    Long publish(String topic, Object message);
}
