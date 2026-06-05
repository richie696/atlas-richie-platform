package com.richie.component.redis.streammq.function;

import com.richie.component.cache.function.CacheFunction;
import com.richie.contract.model.BaseStreamMessage;
import com.richie.component.redis.streammq.stream.StreamMessageEvent;
import reactor.core.publisher.Flux;

/**
 * Stream消息队列API管理器接口，封装了Redis Stream数据结构的可靠消息队列能力。
 * <p>
 * <b>与发布订阅（Pub/Sub）的区别：</b>
 * <ul>
 *   <li><b>Stream消息队列（opsForStream）</b>：基于Redis Stream，消息持久化存储，支持消费组、消息确认、消息堆积和回溯，适合可靠消息、异步任务、日志收集等场景。</li>
 *   <li><b>发布订阅（convertAndSend）</b>：基于Redis的Pub/Sub机制，消息只在内存中，只有在线订阅者能收到，离线后消息丢失，无持久化、无消费确认，适合事件通知、在线推送等场景。</li>
 * </ul>
 * <b>本接口封装了Stream消息队列的可靠消息能力，适用于任务队列、异步处理、日志收集等对可靠性有要求的场景。</b>
 *
 * @author richie696
 * @version 2.0
 * @since 2025-06-25 17:51:00
 */
public interface StreamFunction extends CacheFunction {

    /**
     * 向指定Stream添加消息（ObjectRecord形式）。
     *
     * @param streamKey Stream键
     * @param dto       任意可序列化的对象，将以ObjectRecord写入
     * @param <T>       消息体类型，需继承 BaseStreamMessage
     * @return 消息ID
     */
    <T extends BaseStreamMessage> String publish(String streamKey, T dto);

    /**
     * 获取消息事件流，用于响应式订阅处理。
     * <p>
     * 使用示例：
     * <pre>{@code
     * streamFunction.messageFlow()
     *     .filter(event -> "order-events".equals(event.streamKey()))
     *     .map(event -> (OrderEvent) event.payload())
     *     .subscribe(order -> {
     *         // 处理消息
     *         processOrder(order);
     *         // 手动ACK
     *         streamFunction.acknowledge(event.streamKey(), event.group(), event.recordId().value());
     *     });
     * }</pre>
     *
     * @return 消息事件流
     */
    Flux<StreamMessageEvent<?>> messageFlow();

    /**
     * 手动ACK消息。
     *
     * @param streamKey Stream键
     * @param group     消费者组
     * @param recordId  消息ID
     */
    void acknowledge(String streamKey, String group, String recordId);

}
