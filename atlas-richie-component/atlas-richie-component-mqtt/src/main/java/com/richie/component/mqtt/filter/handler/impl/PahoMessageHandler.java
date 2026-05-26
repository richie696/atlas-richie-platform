package com.richie.component.mqtt.filter.handler.impl;

import com.richie.context.common.api.SpringContextHolder;
import com.richie.component.mqtt.beans.ConsumerMessage;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.filter.datasource.DatasourceHandler;
import com.richie.component.mqtt.filter.handler.MessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 消息处理器接口实现类（用于 Paho MQTT 客户端）
 * <p>
 * 本接口作用包括但不限于给消息队列收发的消息进行加工处理、幂等去重、校验判断等所有和消息处理有关的逻辑。
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>Paho MQTT 客户端：消息格式为 ConsumerMessage</li>
 *   <li>消息的 payload 是 ConsumerMessage 的序列化结果</li>
 * </ul>
 *
 * @author richie696
 * @version 2.0
 * @since 2022-09-16 17:42:58
 */
@Service("pahoMessageHandler")
@RequiredArgsConstructor
public class PahoMessageHandler implements MessageHandler<ConsumerMessage> {

    private final MqttClientProperties properties;

    /**
     * 检查是否是重复消息的方法
     *
     * @param message 待检查的消息对象
     * @return 返回检查结果（true：是重复消息，false：不是重复消息）
     */
    @Override
    public boolean isDuplicate(ConsumerMessage message) {
        if (message == null) {
            return true;
        }
        String hash = message.calcHash();
        return getDatasourceHandler().isDuplicate(hash);
    }

    /**
     * 保存消息数据的方法
     *
     * @param message 待保存的消息
     * @param expired 该消息的过期时间（单位：毫秒）
     */
    @Override
    public void saveCache(ConsumerMessage message, long expired) {
        if (message == null) {
            return;
        }
        String hash = message.calcHash();
        getDatasourceHandler().saveCache(hash, expired);
    }

    private DatasourceHandler getDatasourceHandler() {
        return SpringContextHolder.getBean(properties.getDatasource().getHandlerName());
    }

}
