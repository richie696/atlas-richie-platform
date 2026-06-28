package com.richie.component.nats.pipeline;

import io.nats.client.Message;

/**
 * NATS 消息处理函数式接口
 *
 * <p>所有订阅端消息处理的统一抽象，业务代码实现此接口。
 * 装饰器链通过 {@link NatsMessageHandlerPipeline} 在外层包装横切关注点。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@FunctionalInterface
public interface NatsMessageHandler {

    /**
     * 处理 NATS 消息
     *
     * @param message NATS 原始消息
     * @throws Exception 处理异常
     */
    void handle(Message message) throws Exception;
}
