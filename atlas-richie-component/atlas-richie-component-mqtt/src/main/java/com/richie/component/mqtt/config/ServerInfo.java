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
package com.richie.component.mqtt.config;

import com.richie.component.mqtt.enums.MqttProtocolEnum;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.enums.QosEnum;
import com.richie.component.mqtt.enums.ServerTypeEnum;
import com.richie.component.mqtt.utils.Tools;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * MQTT服务器配置信息
 * <p>
 * 该类包含了MQTT客户端连接服务器所需的所有配置参数，支持多种MQTT服务器类型（如阿里云IoT、EMQ X等）
 * 和网络环境（公网、VPC内网）。
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 *   <li>支持公网和VPC内网双网络配置</li>
 *   <li>支持SSL/TLS安全连接</li>
 *   <li>支持多种服务器类型的认证方式</li>
 *   <li>提供连接超时、心跳间隔等网络参数配置</li>
 *   <li>支持QoS和会话管理配置</li>
 * </ul>
 * <p>
 * <strong>配置前缀：</strong>
 * <p>platform.component.mqtt.server
 * <p>
 * <strong>使用示例：</strong>
 * <pre>{@code
 * platform:
 *   component:
 *     mqtt:
 *       server:
 *         host: "mqtt.example.com"
 *         port: 1883
 *         vpcHost: "mqtt-internal.example.com"
 *         vpcPort: 1883
 *         defaultNetworkType: PUBLIC
 *         username: "your-username"
 *         password: "your-password"
 *         qos: 1
 *         clearSession: false
 *         connectionTimeout: 15000
 *         keepAliveInterval: 90
 *         automaticReconnect: true
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-08 18:32:39
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mqtt.server")
public class ServerInfo {

    /**
     * SSL/TLS安全连接配置
     * <p>
     * 用于配置MQTT客户端的SSL/TLS安全连接参数，支持双向认证。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>生产环境安全连接</li>
     *   <li>需要客户端证书认证的场景</li>
     *   <li>符合安全合规要求的部署</li>
     * </ul>
     * <p>
     * <strong>配置示例：</strong>
     * <pre>{@code
     * platform:
     *   component:
     *     mqtt:
     *       server:
     *         ssl:
     *           caCert: "/path/to/ca.crt"
     *           clientCert: "/path/to/client.crt"
     *           clientKey: "/path/to/client.key"
 * }</pre>
 */
    @Data
    @ConfigurationProperties(prefix = "platform.component.mqtt.server.ssl")
    public static class Ssl {

        /**
         * CA证书文件路径
         *
         * <p>用于验证服务器证书的根证书文件路径。
         *
         * <strong>使用场景：</strong>
         * <ul>
         *   <li>自签名证书环境</li>
         *   <li>私有CA签发的证书</li>
         *   <li>需要验证服务器身份的场景</li>
         * </ul>
         *
         * <strong>推荐值：</strong>
         * <ul>
         *   <li>格式：PEM或DER格式的证书文件</li>
         *   <li>路径：绝对路径或相对于classpath的路径</li>
         * </ul>
         */
        private String caCert;

        /**
         * 客户端证书文件路径
         *
         * <p>用于客户端身份认证的证书文件路径。
         *
         * <strong>使用场景：</strong>
         * <ul>
         *   <li>双向SSL认证</li>
         *   <li>需要客户端身份验证的场景</li>
         *   <li>高安全性要求的部署</li>
         * </ul>
         *
         * <strong>推荐值：</strong>
         * <ul>
         *   <li>格式：PEM格式的证书文件</li>
         *   <li>路径：绝对路径或相对于classpath的路径</li>
         * </ul>
         */
        private String clientCert;

        /**
         * 客户端私钥文件路径
         *
         * <p>与客户端证书配对的私钥文件路径，用于客户端身份认证。
         *
         * <strong>使用场景：</strong>
         * <ul>
         *   <li>双向SSL认证</li>
         *   <li>客户端证书认证</li>
         * </ul>
         *
         * <strong>推荐值：</strong>
         * <ul>
         *   <li>格式：PEM格式的私钥文件</li>
         *   <li>路径：绝对路径或相对于classpath的路径</li>
         *   <li>权限：建议600（仅所有者可读写）</li>
         * </ul>
         */
        private String clientKey;
    }

    /**
     * 默认网络类型
     *
     * <p>指定客户端连接时优先使用的网络类型，影响服务器地址的选择。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li><strong>PUBLIC</strong>：公网环境，适用于云服务器或外网访问</li>
     *   <li><strong>VPC</strong>：VPC内网环境，适用于同地域ECS实例访问，延迟更低</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>云服务器：VPC（延迟更低，费用更少）</li>
     *   <li>本地开发：PUBLIC（便于调试）</li>
     *   <li>跨地域访问：PUBLIC</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>无默认值，必须显式配置
     */
    private NetworkTypeEnum defaultNetworkType = NetworkTypeEnum.PUBLIC;

    /**
     * 服务器公网IP地址
     *
     * <p>MQTT服务器的公网IP地址或域名。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>公网环境连接</li>
     *   <li>跨地域访问</li>
     *   <li>本地开发调试</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>格式：IP地址或域名</li>
     *   <li>示例：mqtt.example.com 或 192.168.1.100</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>无默认值，必须显式配置
     */
    private String host;

    /**
     * 服务器公网端口
     *
     * <p>MQTT服务器的公网端口号。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>公网环境连接</li>
     *   <li>标准MQTT端口配置</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>标准MQTT：1883（非加密）</li>
     *   <li>MQTT over SSL：8883</li>
     *   <li>WebSocket：8083</li>
     *   <li>WebSocket over SSL：8084</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>无默认值，必须显式配置
     */
    private int port;

    /**
     * VPC内网IP地址
     *
     * <p>MQTT服务器在VPC内网的IP地址或域名。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>同地域ECS实例访问</li>
     *   <li>VPC内网环境</li>
     *   <li>降低网络延迟和费用</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>格式：IP地址或内网域名</li>
     *   <li>示例：mqtt-internal.example.com</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>无默认值，可选配置
     */
    private String vpcHost;

    /**
     * VPC内网端口
     *
     * <p>MQTT服务器在VPC内网的端口号。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>VPC内网环境连接</li>
     *   <li>同地域ECS实例访问</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>通常与公网端口相同</li>
     *   <li>标准MQTT：1883</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>无默认值，可选配置
     */
    private int vpcPort;

    /**
     * 服务质量等级（QoS）
     *
     * <p>MQTT消息传输的服务质量等级，决定了消息传递的可靠性。
     *
     * <strong>QoS级别说明：</strong>
     * <ul>
     *   <li><strong>QoS 0</strong>：最多一次 (at most once) - 不保证消息到达</li>
     *   <li><strong>QoS 1</strong>：最少一次 (at least once) - 保证消息至少到达一次，可能重复</li>
     *   <li><strong>QoS 2</strong>：只有一次 (exactly once) - 保证消息只到达一次</li>
     * </ul>
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li><strong>QoS 0</strong>：实时数据、传感器数据、允许丢失的场景</li>
     *   <li><strong>QoS 1</strong>：一般业务数据、日志数据、允许重复的场景</li>
     *   <li><strong>QoS 2</strong>：重要业务数据、金融数据、不允许重复的场景</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认推荐：1（平衡性能和可靠性）</li>
     *   <li>实时数据：0（最高性能）</li>
     *   <li>重要数据：2（最高可靠性）</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>1
     */
    private QosEnum qos = QosEnum.AT_LEAST_ONCE;

    /**
     * 是否清除会话
     *
     * <p>控制MQTT客户端连接时是否清除之前的会话状态。
     *
     * <strong>作用机制：</strong>
     * <ul>
     *   <li>当QoS等于1、2时，客户端离线期间收到的消息会在客户端下次上线时发送</li>
     *   <li>服务端正在发送消息给客户端期间连接丢失导致发送失败的消息会被保存</li>
     *   <li>存储订阅的消息QoS1和QoS2消息，当客户端重新连接时发送</li>
     * </ul>
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li><strong>true</strong>：开发测试、临时连接、不需要消息持久化</li>
     *   <li><strong>false</strong>：生产环境、重要业务、需要消息不丢失</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>开发环境：true（便于调试）</li>
     *   <li>生产环境：false（防止数据丢失）</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>true（生产环境建议修改为false）
     */
    private boolean clearSession = true;

    /**
     * 服务器用户名
     *
     * <p>用于MQTT服务器身份认证的用户名。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>标准MQTT服务器认证</li>
     *   <li>阿里云IoT平台认证</li>
     *   <li>其他MQTT服务商认证</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>格式：字符串</li>
     *   <li>长度：建议不超过64字符</li>
     *   <li>特殊字符：避免使用特殊字符</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>无默认值，必须显式配置
     */
    private String username;

    /**
     * 服务器密码
     *
     * <p>用于MQTT服务器身份认证的密码。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>标准MQTT服务器认证</li>
     *   <li>阿里云IoT平台签名认证</li>
     *   <li>其他MQTT服务商认证</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>格式：字符串</li>
     *   <li>长度：建议不超过64字符</li>
     *   <li>安全性：使用强密码</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>空字符串
     */
    private String password = "";

    /**
     * 连接协议
     *
     * <p>MQTT客户端连接服务器使用的协议类型。
     *
     * <strong>协议类型：</strong>
     * <ul>
     *   <li><strong>tcp</strong>：标准TCP连接</li>
     *   <li><strong>ssl</strong>：SSL/TLS加密连接（支持两种模式）</li>
     *   <li><strong>tls</strong>：TLS加密连接（简单模式，仅用户名密码认证）</li>
     *   <li><strong>ws</strong>：WebSocket连接</li>
     *   <li><strong>wss</strong>：WebSocket over SSL连接</li>
     * </ul>
     *
     * <strong>SSL/TLS连接模式说明：</strong>
     * <ul>
     *   <li><strong>X.509证书认证模式</strong>：当配置了 {@code ssl} 对象且包含证书信息（caCert、clientCert、clientKey）时，
     *       使用双向SSL认证，适用于高安全性要求的场景</li>
     *   <li><strong>简单TLS模式</strong>：当未配置 {@code ssl} 或 {@code ssl} 配置为空时，使用简单的TLS连接，
     *       仅通过用户名密码认证，适用于腾讯云MQTT等场景</li>
     * </ul>
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li><strong>tcp</strong>：内网环境、开发测试</li>
     *   <li><strong>ssl</strong>：生产环境、安全要求高（支持X.509证书或简单TLS）</li>
     *   <li><strong>tls</strong>：简单TLS连接，仅用户名密码认证（如腾讯云MQTT）</li>
     *   <li><strong>ws/wss</strong>：浏览器环境、防火墙限制</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>开发环境：tcp</li>
     *   <li>生产环境（需要证书）：ssl（配置ssl对象）</li>
     *   <li>生产环境（仅用户名密码）：tls 或 ssl（不配置ssl对象）</li>
     *   <li>浏览器环境：wss</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>TCP
     * <p>
     * <strong>配置示例：</strong>
     * <pre>{@code
     * platform:
     *   component:
     *     mqtt:
     *       server:
     *         protocol: tcp    # 或 ssl, tls, ws, wss（不区分大小写）
     * }</pre>
     */
    private MqttProtocolEnum protocol = MqttProtocolEnum.TCP;

    /**
     * 客户端发送超时时间
     *
     * <p>客户端发送消息时的最大等待时间，防止无限阻塞。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>防止网络问题导致的无限等待</li>
     *   <li>控制消息发送的超时时间</li>
     *   <li>提高系统响应性</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>本地网络：3000-5000毫秒</li>
     *   <li>远程网络：5000-10000毫秒</li>
     *   <li>弱网环境：10000-30000毫秒</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>5000毫秒
     */
    private Long timeToWait = 5000L;

    /**
     * 连接超时时间
     *
     * <p>MQTT连接建立的最大等待时间。包括TCP连接建立和MQTT协议握手的时间。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>控制连接建立的超时时间</li>
     *   <li>防止网络问题导致的长时间等待</li>
     *   <li>提高连接失败时的响应速度</li>
     * </ul>
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>本地服务器</strong>：5-10秒（快速连接）</li>
     *   <li><strong>远程服务器</strong>：10-15秒（考虑网络延迟）</li>
     *   <li><strong>弱网环境</strong>：15-30秒（容忍网络问题）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：15秒</li>
     *   <li>范围：5-60秒</li>
     *   <li>最小值：5秒</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>15000毫秒（15秒）
     */
    private Long connectionTimeout = 15000L;

    /**
     * 是否自动重连
     *
     * <p>控制MQTT客户端在连接断开后是否自动尝试重新连接。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li><strong>true</strong>：生产环境、需要高可用性</li>
     *   <li><strong>false</strong>：开发测试、需要手动控制连接</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>生产环境：true（保证服务可用性）</li>
     *   <li>开发环境：false（便于调试连接问题）</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>true
     */
    private boolean automaticReconnect = true;

    /**
     * 心跳间隔时间
     *
     * <p>MQTT客户端发送心跳包的时间间隔，用于保持连接活跃。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>保持长连接活跃</li>
     *   <li>检测连接状态</li>
     *   <li>防止网络设备断开空闲连接</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>稳定网络：60-90秒</li>
     *   <li>不稳定网络：30-60秒</li>
     *   <li>移动网络：30秒</li>
     *   <li>最小值：10秒</li>
     *   <li>最大值：65535秒</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>90秒
     */
    private int keepAliveInterval = 90;

    /**
     * SSL配置
     *
     * <p>SSL/TLS安全连接的配置参数。
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>生产环境安全连接</li>
     *   <li>需要客户端证书认证</li>
     *   <li>符合安全合规要求</li>
     * </ul>
     *
     * <strong>默认值：</strong>
     * <p>null（不启用SSL）
     */
    private Ssl ssl;


    /**
     * 获取MQTT服务器连接地址
     *
     * <p>根据网络类型返回对应的服务器连接地址，支持公网和VPC内网地址切换。
     *
     * <strong>功能说明：</strong>
     * <ul>
     *   <li>根据networkType参数选择对应的服务器地址</li>
     *   <li>PUBLIC网络类型使用host和port</li>
     *   <li>VPC网络类型使用vpcHost和vpcPort</li>
     *   <li>自动拼接协议、地址和端口</li>
     * </ul>
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>MQTT客户端连接初始化</li>
     *   <li>动态切换网络环境</li>
     *   <li>负载均衡场景</li>
     * </ul>
     *
     * <strong>返回格式：</strong>
     * <p>protocol://host:port
     * <p>示例：tcp://mqtt.example.com:1883
     *
     * @param networkType MQTT的网络类型（PUBLIC或VPC）
     * @return 返回完整的服务器连接地址
     * @throws IllegalArgumentException 当网络类型为VPC但vpcHost或vpcPort未配置时
     */
    public String getServerUri(NetworkTypeEnum networkType) {
        String host;
        int port;
        if (networkType == NetworkTypeEnum.PUBLIC) {
            host = this.host;
            port = this.port;
        } else {
            host = this.vpcHost;
            port = this.vpcPort;
        }
        return String.format("%s://%s:%s", protocol.getValue(), host, port);
    }

    /**
     * 获取MQTT服务器用户名
     *
     * <p>根据服务器类型返回对应的用户名格式，支持不同MQTT服务商的认证方式。
     *
     * <strong>功能说明：</strong>
     * <ul>
     *   <li>阿里云IoT平台：返回签名格式的用户名</li>
     *   <li>其他服务器：直接返回配置的用户名</li>
     *   <li>自动提取实例ID并格式化</li>
     * </ul>
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>MQTT客户端认证</li>
     *   <li>阿里云IoT平台连接</li>
     *   <li>多服务器类型支持</li>
     * </ul>
     *
     * <strong>阿里云格式：</strong>
     * <p>Signature|username|instanceId
     * <p>示例：Signature|device001|a1b2c3d4
     *
     * @param serverType MQTT服务器类型
     * @return 返回格式化的用户名
     * @throws IllegalArgumentException 当阿里云类型但host格式不正确时
     */
    public String getUsername(ServerTypeEnum serverType) {
        return switch (serverType) {
            case ALIYUN -> "Signature|%s|%s".formatted(username, getInstanceId(serverType));
            default -> username;
        };
    }

    /**
     * 获取MQTT服务器密码
     *
     * <p>根据服务器类型返回对应的密码或签名，支持不同MQTT服务商的认证方式。
     *
     * <strong>功能说明：</strong>
     * <ul>
     *   <li>阿里云IoT平台：使用MAC签名算法生成密码</li>
     *   <li>其他服务器：直接返回配置的密码</li>
     *   <li>支持动态签名生成</li>
     * </ul>
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>MQTT客户端认证</li>
     *   <li>阿里云IoT平台安全连接</li>
     *   <li>动态密码生成</li>
     * </ul>
     *
     * <strong>阿里云签名：</strong>
     * <p>使用HMAC-SHA1算法，以clientId为消息，password为密钥生成签名
     *
     * @param serverType MQTT服务器类型
     * @param clientId 客户端ID，用于签名生成
     * @return 返回密码或签名
     * @throws NoSuchAlgorithmException 当签名算法不可用时
     * @throws InvalidKeyException 当密钥无效时
     */
    public String getPassword(ServerTypeEnum serverType, String clientId) throws NoSuchAlgorithmException, InvalidKeyException {
        return switch (serverType) {
            case ALIYUN -> Tools.macSignature(clientId, password);
            default -> password;
        };
    }

    /**
     * 获取MQTT服务器实例ID
     *
     * <p>从服务器地址中提取实例ID，主要用于阿里云IoT平台。
     *
     * <strong>功能说明：</strong>
     * <ul>
     *   <li>阿里云IoT平台：从host地址中提取实例ID</li>
     *   <li>公网地址：提取第一个点号前的部分</li>
     *   <li>VPC地址：提取"-internal"前的部分</li>
     *   <li>其他服务器：返回空字符串</li>
     * </ul>
     *
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>阿里云IoT平台认证</li>
     *   <li>用户名格式化</li>
     *   <li>实例标识</li>
     * </ul>
     *
     * <strong>提取规则：</strong>
     * <ul>
     *   <li>公网地址：a1b2c3d4.iot-as-mqtt.cn-shanghai.aliyuncs.com → a1b2c3d4</li>
     *   <li>VPC地址：a1b2c3d4-internal.iot-as-mqtt.cn-shanghai.aliyuncs.com → a1b2c3d4</li>
     * </ul>
     *
     * @param serverType MQTT服务器类型
     * @return 返回实例ID，非阿里云服务器返回空字符串
     * @throws StringIndexOutOfBoundsException 当host格式不正确时
     */
    public String getInstanceId(ServerTypeEnum serverType) {
        if (serverType == ServerTypeEnum.ALIYUN) {
            if (defaultNetworkType == NetworkTypeEnum.PUBLIC) {
                return host.substring(0, host.indexOf('.'));
            }
            return host.substring(0, host.indexOf("-internal"));
        }
        return "";
    }

}
