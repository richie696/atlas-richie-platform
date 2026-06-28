package com.richie.component.nats.strategy;

import io.nats.client.impl.Headers;

/**
 * NATS Header 注入策略接口（发送端）
 *
 * <p>将 {@code HeaderContextHolder} 中的上下文信息注入到 NATS 消息 Headers 中。
 * 在 publish / request 操作前自动调用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsHeaderInjector {

    /**
     * 将当前线程上下文中的头信息注入到 NATS Headers
     *
     * @param headers NATS 消息 Headers
     */
    void inject(Headers headers);
}
