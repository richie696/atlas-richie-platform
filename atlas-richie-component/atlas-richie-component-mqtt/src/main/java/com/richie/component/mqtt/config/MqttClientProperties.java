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
package com.richie.component.mqtt.config;

import com.richie.component.mqtt.enums.ClientTypeEnum;
import com.richie.component.mqtt.enums.DatasourceTypeEnum;
import com.richie.component.mqtt.enums.ServerTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT客户端属性配置信息
 * <p>
 * 用于配置MQTT客户端的各种属性，包括服务器信息、客户端ID、MQTT版本等。
 *
 * @author richie696
 * @version 2.1
 * @since 2022-09-08 18:34:02
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mqtt")
public class MqttClientProperties {

    /**
     * 是否启用（true：启用，false：禁用）
     */
    private Boolean enable = false;

    /**
     * 是否在启动时直接加载MQTT Client（true：加载，false：不加载）
     */
    private Boolean initClient = false;

    /**
     * MQTT 服务器类型
     */
    private ServerTypeEnum type = ServerTypeEnum.GENERAL;

    /**
     * 幂等去重使用的数据缓存源
     */
    private DatasourceTypeEnum datasource = DatasourceTypeEnum.MEMORY;

    /**
     * 当前MQTT Client是服务端还是客户端
     */
    private ClientTypeEnum clientType = ClientTypeEnum.CLIENT;

    /**
     * 当前MQTT Client是服务端还是客户端
     */
    private String mqttVersion = "mqtt_5_0";

    /**
     * 根Topic名称
     */
    private String parentTopic;

    /**
     * 客户端分组ID
     */
    private String groupId;

    /**
     * MQTT 服务器配置
     */
    private ServerInfo server;

    /**
     * 客户端ID生成器Bean名称
     * <p>
     * 可选值：
     * <ul>
     *   <li>defaultClientIdRuler：默认实现，格式为 {@code 前缀-版本-硬件指纹-随机串}，不限制长度（默认）</li>
     *   <li>mqtt5ClientIdRuler：MQTT 5.0 规范实现，严格限制在 1-23 字节之间</li>
     * </ul>
     * <p>
     * 配置示例：
     * <pre>{@code
     * platform:
     *   component:
     *     mqtt:
     *       client-id-ruler: defaultClientIdRuler  # 使用默认实现（旧格式）
     *       # client-id-ruler: mqtt5ClientIdRuler   # 使用 MQTT 5.0 规范实现
     * }</pre>
     */
    private String clientIdRuler = "defaultClientIdRuler";

    /**
     * MQTT 5.0 专用配置
     */
    private Mqtt5Config mqtt5 = new Mqtt5Config();

    /**
     * 快速恢复配置
     */
    private FastRecoveryConfig fastRecovery = new FastRecoveryConfig();

}
