/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.beans;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import com.richie.component.mqtt.enums.QosEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Map;

/**
 * 消费者消息接口
 *
 * @author richie696
 * @version 2.0
 * @since 2022-09-13 11:07:24
 */
@Slf4j
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerMessage implements Serializable {

    /**
     * 消息来源主题
     */
    private String topic;
    /**
     * 消息报文字节数组
     */
    @Getter
    private byte[] payload;
    /**
     * QoS级别
     */
    private int qos = 1;
    /**
     * 是否在服务器保留本条消息（服务器仅会保留最新的1条）
     */
    private boolean retained = false;
    /**
     * 消息时间戳（毫秒）
     */
    @Getter
    private long timestamp;
    /**
     * 消息 ID
     */
    private String messageId;
    /**
     * 消息属性
     * <p>注：该属性仅用于服务消费者接收到的消息，服务发布者不需要使用该属性。
     */
    private Map<String, String> properties;


    /**
     * 验证QoS值是否有效的方法
     *
     * @param qos 待验证的 QoS 值
     * @throws IllegalArgumentException 如果 QoS 值不等于0、1、2则抛出错误参数异常.
     */
    public static void validateQos(int qos) {
        if (QosEnum.isValid(qos)) {
            return;
        }
        // 如果无效则抛出QoS值无效异常
        throw new IllegalArgumentException("The qos value is invalid.");
    }

    /**
     * 根据消息报文字节数组创建消息的构造函数
     * <p style="color: red">（注：通过本构造函数创建的消息体禁止消费者进行修改操作）
     *
     * <p>其它默认值为：
     * <ul>
     *    <li>消息 QoS 值默认为：1</li>
     *    <li>消息的 retained 值为：false，消息将不会被服务器"保留"</li>
     * </ul>
     *
     * @param payload The Bytearray of the payload
     */
    public ConsumerMessage(byte[] payload) {
        this.payload = payload;
    }

    /**
     * 获取字符串类型的消息报文的方法
     *
     * @return 返回消息报文字符串
     */
    @JsonIgnore
    public String getPayloadString() {
        return new String(payload);
    }

    /**
     * 获取指定类型的消息体（将字节数组转换为消息体）
     *
     * @param cls 消息体类型
     * @param <T> 消息体具体的类型
     * @return 返回消息体
     * @throws IllegalArgumentException 当消息体无法正确转换时抛出该异常
     */
    @JsonIgnore
    public <T> T getBody(Class<T> cls) {
        return JsonUtils.getInstance().deserialize(getPayloadString(), cls);
    }

    /**
     * 获取 Payload 哈希值的方法
     * @return 返回 Payload 哈希值
     */
    public String calcHash() {
        return HashUtils.sha256(payload);
    }

}
