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

import com.richie.component.mqtt.enums.NetworkTypeEnum;
import lombok.Data;

/**
 * 连接状态事件，表示MQTT连接状态变化（包含MQTT的连接状态和网络状态）。
 *
 * @author richie696
 * @version 1.0
 * @since 2025/7/22
 */
@Data
public class ConnectionStateEvent {

    private String clientId;

    private ConnectionState state;

    private NetworkTypeEnum networkType;

    private long timestamp;

    private NetworkQualityEvent networkQuality;

    /**
     * 构造连接状态事件
     *
     * @param clientId   客户端ID
     * @param state      连接状态
     * @param networkType 网络类型
     * @param timestamp  时间戳
     */
    public ConnectionStateEvent(String clientId, ConnectionState state, NetworkTypeEnum networkType, long timestamp) {
        this.clientId = clientId;
        this.state = state;
        this.networkType = networkType;
        this.timestamp = timestamp;
    }

    /**
     * 构造连接状态事件（包含网络质量信息）
     *
     * @param clientId       客户端ID
     * @param state          连接状态
     * @param networkType    网络类型
     * @param timestamp      时间戳
     * @param networkQuality 网络质量事件
     */
    public ConnectionStateEvent(String clientId, ConnectionState state, NetworkTypeEnum networkType, long timestamp, NetworkQualityEvent networkQuality) {
        this.clientId = clientId;
        this.state = state;
        this.networkType = networkType;
        this.timestamp = timestamp;
        this.networkQuality = networkQuality;
    }

    /**
     * 创建连接状态事件（使用当前时间戳）
     *
     * @param clientId   客户端ID
     * @param state      连接状态
     * @param networkType 网络类型
     * @return 连接状态事件实例
     */
    public static ConnectionStateEvent of(String clientId, ConnectionState state, NetworkTypeEnum networkType) {
        return new ConnectionStateEvent(clientId, state, networkType, System.currentTimeMillis());
    }

    /**
     * 创建连接状态事件（使用当前时间戳，包含网络质量信息）
     *
     * @param clientId       客户端ID
     * @param state          连接状态
     * @param networkType    网络类型
     * @param networkQuality 网络质量事件
     * @return 连接状态事件实例
     */
    public static ConnectionStateEvent of(String clientId, ConnectionState state, NetworkTypeEnum networkType, NetworkQualityEvent networkQuality) {
        return new ConnectionStateEvent(clientId, state, networkType, System.currentTimeMillis(), networkQuality);
    }

}
