package com.richie.component.mqtt.beans;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * MQTT遗嘱消息
 * <p>
 * 用于表示MQTT客户端的遗嘱消息，当客户端异常断开连接时，服务器会发布此消息。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-11 10:31:40
 */
@Data
@Accessors(chain = true)
public class WillMessage implements Serializable {
    /**
     * 门店 ID
     */
    private String storeId;
    /**
     * 客户端 ID
     */
    private String clientId;
    /**
     * 状态
     */
    private String status;
    /**
     * 断开原因
     */
    private String reason;
    /**
     * 网络类型
     */
    private String networkType;
    /**
     * 应用版本
     */
    private String appVersion;
    /**
     * 持续连接时间
     */
    private long connectionDuration;
}
