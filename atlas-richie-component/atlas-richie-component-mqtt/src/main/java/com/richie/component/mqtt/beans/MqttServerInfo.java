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

import com.richie.component.mqtt.config.ServerInfo;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.enums.ServerTypeEnum;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Objects;

/**
 * MQTT 服务器信息
 * <p>
 * 用于表示MQTT服务器的信息，包括服务器类型、地址、端口等。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-19 17:08:53
 */
@Data
@Accessors(chain = true)
@Builder
public class MqttServerInfo implements Serializable {

    /**
     * MQTT 服务器类型
     */
    private ServerTypeEnum serverType;

    /**
     * 网络类型
     */
    private NetworkTypeEnum networkType;

    /**
     * 协议
     */
    private String protocol;

    /**
     * 公网服务地址
     */
    private String host;

    /**
     * 公网服务端口
     */
    private Integer port;

    /**
     * VPC服务地址
     */
    private String vpcHost;

    /**
     * VPC服务端口
     */
    private Integer vpcPort;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 根主题
     */
    private String parentTopic;

    /**
     * 分组ID
     */
    private String groupId;

    /**
     * 客户端标识ID
     */
    private String deviceId;

    /**
     * SSL配置
     */
    private ServerInfo.Ssl ssl;

    /**
     * 检查服务器信息是否无效
     * <p>
     * 如果公网和VPC的服务器信息都无效，则返回true。
     *
     * @return 如果服务器信息无效返回true，否则返回false
     */
    public boolean isInvalid() {
        return (Objects.isNull(host) || Objects.isNull(port)) && (Objects.isNull(vpcHost) || Objects.isNull(vpcPort));
    }

    /**
     * 检查VPC服务器信息是否有效
     *
     * @return 如果VPC服务器信息有效返回true，否则返回false
     */
    public boolean isValidOfVpc() {
        return Objects.nonNull(vpcHost) && Objects.nonNull(vpcPort);
    }

    /**
     * 检查公网服务器信息是否有效
     *
     * @return 如果公网服务器信息有效返回true，否则返回false
     */
    public boolean isValidOfPublic() {
        return Objects.nonNull(host) && Objects.nonNull(port);
    }
}
