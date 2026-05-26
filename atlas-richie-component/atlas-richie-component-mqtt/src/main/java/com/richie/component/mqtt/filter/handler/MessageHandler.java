package com.richie.component.mqtt.filter.handler;

/**
 * 消息处理器接口
 * <p>
 * 本接口作用包括但不限于给消息队列收发的消息进行加工处理、幂等去重、校验判断等所有和消息处理有关的逻辑。
 * <p>
 * <strong>泛型设计：</strong>
 * <ul>
 *   <li><strong>Paho MQTT 客户端</strong>：使用 {@code MessageHandler<ConsumerMessage>}，消息格式为 ConsumerMessage</li>
 *   <li><strong>HiveMQ MQTT 客户端</strong>：使用 {@code MessageHandler<Mqtt5Publish>}，消息格式为 Mqtt5Publish（payload 直接是业务数据）</li>
 * </ul>
 * <p>
 * <strong>实现类：</strong>
 * <ul>
 *   <li>{@code MessageHandlerImpl<ConsumerMessage>}：用于 Paho MQTT 客户端</li>
 *   <li>{@code HiveMqMessageHandlerImpl<Mqtt5Publish>}：用于 HiveMQ MQTT 客户端</li>
 * </ul>
 *
 * @param <T> 消息类型，Paho 使用 ConsumerMessage，HiveMQ 使用 Mqtt5Publish
 * @author richie696
 * @version 2.0
 * @since 2022-09-16 17:42:58
 */
public interface MessageHandler<T> {

    /**
     * 检查是否是重复消息的方法
     *
     * @param message 待检查的消息对象
     * @return 返回检查结果（true：是重复消息，false：不是重复消息）
     */
    boolean isDuplicate(T message);

    /**
     * 保存消息数据的方法
     *
     * @param message 待保存的消息
     * @param expired 该消息的过期时间（单位：毫秒）
     */
    void saveCache(T message, long expired);

}
