package com.richie.component.mqtt.generator;

/**
 * 客户端ID生成规则接口
 * <p>
 * 定义MQTT客户端ID的生成规则，不同的实现可以提供不同的生成策略。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-13
 */
public interface ClientIdRuler {

    /**
     * 获取客户端ID
     *
     * @return 客户端ID字符串
     */
    String getClientId();

}
