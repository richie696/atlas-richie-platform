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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 心跳事件
 * <p>
 * 用于表示MQTT客户端的心跳事件，包含主题和心跳信息。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-20
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatEvent implements Serializable {

    /**
     * 心跳主题
     */
    private String topic;

    /**
     * 心跳信息
     * <p>
     * 包含客户端ID、时间戳和心跳计数等信息。
     */
    private HeartbeatInfo heartbeat;

    /**
     * 创建心跳事件实例
     *
     * @param topic     心跳主题
     * @param heartbeat 心跳信息
     * @return 心跳事件实例
     */
    public static HeartbeatEvent of(String topic, HeartbeatInfo heartbeat) {
        return new HeartbeatEvent(topic, heartbeat);
    }
}
