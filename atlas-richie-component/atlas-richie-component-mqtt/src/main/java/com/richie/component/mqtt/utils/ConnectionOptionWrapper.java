package com.richie.component.mqtt.utils;

import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.config.ServerInfo;
import com.richie.component.mqtt.enums.MqttProtocolEnum;
import com.richie.component.mqtt.enums.ServerTypeEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import javax.net.ssl.SSLSocketFactory;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.MQTT_VERSION_3_1_1;

/**
 * 工具类：负责封装 MQ4IOT 客户端的初始化参数设置
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-21 14:04:43
 */
@Slf4j
public class ConnectionOptionWrapper {
    /**
     * 内部连接参数
     */
    @Getter
    private final MqttConnectOptions mqttConnectOptions;

    /**
     * MQTT SSL配置信息
     */
    private final ServerInfo.Ssl ssl;

    /**
     * 账号 accesskey，从账号系统控制台获取
     */
    private final String username;
    /**
     * 账号 secretKey，从账号系统控制台获取，仅在Signature鉴权模式下需要设置
     */
    private final char[] password;
    /**
     * MQ4IOT clientId，由业务系统分配，需要保证每个 tcp 连接都不一样，保证全局唯一，如果不同的客户端对象（tcp 连接）使用了相同的 clientId 会导致连接异常断开。
     * clientId 由两部分组成，格式为 GroupID@@@DeviceId，其中 groupId 在 MQ4IOT 控制台申请，DeviceId 由业务方自己设置，clientId 总长度不得超过64个字符。
     */
    @Getter
    private final String clientId;
    /**
     * 是否清除会话
     * <p>取值范围：true / false
     * <p>默认值：true（生产环境建议修改为false，否则有可能造成数据丢失）
     * <p>作用：MQTT服务器在客户端断开之后是否继续保留客户端的订阅状态(状态内包含队列消息)
     * <ul>
     *     <li>当QoS等于1、2时，客户端离线期间，收到的消息会在客户端下次上线时发送；</li>
     *     <li>当服务器正在发送消息给客户端的</li>
     * </ul>
     * 存储订阅的消息Qos1和Qos2消息，当客户端重新订阅时发送
     * 服务端正在发送消息给客户端期间连接丢失导致发送失败的消息
     */
    private final boolean cleanSession;

    /**
     * 客户端连接超时时间，防止无限阻塞
     */
    private final int connectionTimeout;

    private boolean automaticReconnect;

    private int keepAliveInterval;

    /**
     * 连接协议类型
     */
    private final MqttProtocolEnum protocol;

    private static final int MQTT_VERSION = MQTT_VERSION_3_1_1;

    /**
     * 客户端使用的 Token 参数，仅在 Token 鉴权模式下需要设置，Key 为 token 类型，一个客户端最多存在三种类型，R，W，RW，Value 是 token内容。
     * 应用需要保证 token 在过期及时更新。否则会导致连接异常。
     */
    @Getter
    private final Map<String, String> tokenData = new ConcurrentHashMap<>();

    /**
     * Signature 鉴权模式下构造方法
     *
     * @param properties MQ4IOT 实例 ID，购买后控制台获取
     * @param clientId   MQ4IOT clientId，由业务系统分配
     * @throws NoSuchAlgorithmException 当算法不匹配时抛出该异常
     * @throws InvalidKeyException      当密钥无效的时候抛出该异常
     */
    public ConnectionOptionWrapper(MqttClientProperties properties, String clientId) throws NoSuchAlgorithmException, InvalidKeyException {
        var type = properties.getType();
        var serverInfo = properties.getServer();
        this.username = serverInfo.getUsername(type);
        this.password = serverInfo.getPassword(type, clientId).toCharArray();
        this.cleanSession = serverInfo.isClearSession();
        this.connectionTimeout = serverInfo.getConnectionTimeout().intValue();
        this.automaticReconnect = serverInfo.isAutomaticReconnect();
        this.keepAliveInterval = serverInfo.getKeepAliveInterval();
        this.ssl = serverInfo.getSsl();
        this.protocol = serverInfo.getProtocol();
        this.clientId = clientId;
        mqttConnectOptions = new MqttConnectOptions();
        refreshOptions();
    }

    /**
     * Signature 鉴权模式下构造方法
     *
     * @param clientId   MQ4IOT clientId，由业务系统分配
     * @param type       服务器类型
     * @param serverInfo 服务器配置信息
     * @throws NoSuchAlgorithmException 当算法不匹配时抛出该异常
     * @throws InvalidKeyException      当密钥无效的时候抛出该异常
     */
    public ConnectionOptionWrapper(String clientId, ServerTypeEnum type, ServerInfo serverInfo) throws NoSuchAlgorithmException, InvalidKeyException {
        this.username = serverInfo.getUsername(type);
        this.password = serverInfo.getPassword(type, clientId).toCharArray();
        this.cleanSession = serverInfo.isClearSession();
        this.connectionTimeout = serverInfo.getConnectionTimeout().intValue();
        this.automaticReconnect = serverInfo.isAutomaticReconnect();
        this.keepAliveInterval = serverInfo.getKeepAliveInterval();
        mqttConnectOptions = new MqttConnectOptions();
        this.ssl = serverInfo.getSsl();
        this.protocol = serverInfo.getProtocol();
        this.clientId = clientId;
        refreshOptions();
    }


    private void refreshOptions() {
        mqttConnectOptions.setUserName(this.username);
        mqttConnectOptions.setPassword(this.password);
        mqttConnectOptions.setCleanSession(this.cleanSession);
        mqttConnectOptions.setKeepAliveInterval(this.keepAliveInterval);
        mqttConnectOptions.setConnectionTimeout(this.connectionTimeout);
        mqttConnectOptions.setAutomaticReconnect(this.automaticReconnect);
        mqttConnectOptions.setMqttVersion(MQTT_VERSION);
        
        // 判断是否需要SSL/TLS连接
        if (MqttSslUtils.needsSsl(protocol)) {
            try {
                // 使用公共工具类创建 SSL/TLS Socket Factory
                SSLSocketFactory socketFactory = MqttSslUtils.createSocketFactory(ssl, protocol);
                mqttConnectOptions.setSocketFactory(socketFactory);
                // 同时发送的消息数量增加到1000，防止消息挤压超过默认的10条以后会报错的问题
                mqttConnectOptions.setMaxInflight(1000);
            } catch (Exception e) {
                log.error("ssl/tls connect error: {}", e.getMessage(), e);
            }
        }
    }
}
