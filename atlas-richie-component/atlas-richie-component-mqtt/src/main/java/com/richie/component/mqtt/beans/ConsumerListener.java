/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.beans;

import com.richie.component.mqtt.enums.QosEnum;
import lombok.Builder;
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
@Builder
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

    /**
     * 订阅 QoS
     * <p>
     * 指定该主题的订阅 QoS，为 null 时使用全局配置中的默认 QoS。
     */
    private QosEnum qos;

    public static ConsumerListener create(String topic, Consumer<ConsumerMessage> callback) {
        return ConsumerListener.builder().topic(topic).callback(callback).build();
    }

    public static ConsumerListener create(String topic, Consumer<ConsumerMessage> callback, QosEnum qos) {
        return ConsumerListener.builder().topic(topic).callback(callback).qos(qos).build();
    }

}
