package com.richie.component.nats.strategy;

import io.nats.client.impl.Headers;

/**
 * NATS Header 提取策略接口（接收端）
 *
 * <p>从 NATS 消息 Headers 中提取上下文信息并恢复到 {@code HeaderContextHolder}。
 * 在 subscribe / consume 收到消息时自动调用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsHeaderExtractor {

    /**
     * 从 NATS Headers 提取头信息并恢复到当前线程上下文
     *
     * @param headers NATS 消息 Headers
     */
    void extract(Headers headers);
}
