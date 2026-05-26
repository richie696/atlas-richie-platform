package com.richie.component.mqtt.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * 消费者回调接口
 * <p>
 * 用于定义MQTT消息消费者的回调函数，包含主题和回调函数。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-15 11:16:57
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor(staticName = "create")
public class ConsumerListener implements Serializable {

    /**
     * 订阅的主题
     */
    private String topic;

    /**
     * 消息回调函数
     * <p>
     * 当收到该主题的消息时，会调用此回调函数处理消息。
     */
    private Consumer<ConsumerMessage> callback;

}
