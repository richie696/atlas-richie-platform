package com.richie.component.cache.function;

import org.springframework.data.redis.connection.MessageListener;

/**
 * Redis Key事件订阅与通知API管理器接口。
 * <p>
 * 封装了基于Redis发布订阅机制的Key事件监听能力，支持对Key过期、删除等事件的订阅与回调处理。
 * 适用于缓存失效通知、分布式事件驱动等场景。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:47:29
 */
public interface EventFunction extends CacheFunction {
    /**
     * 订阅指定模式的Key事件。
     *
     * @param pattern 事件模式（如__keyevent@0__:expired）
     * @param listener 消息监听器，收到事件时回调
     */
    void subscribeKeyEvent(String pattern, MessageListener listener);
}
