package com.richie.component.messaging.pulsar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.binder.pulsar.properties.PulsarBinderConfigurationProperties;
import org.springframework.context.annotation.Primary;

/**
 * Pulsar Binder 配置属性。
 * <p>
 * 配置前缀：{@code spring.cloud.stream.pulsar.binder}，继承 Spring Cloud Stream Pulsar 默认配置。
 *
 * @author richie696
 * @since 2022-09-27
 */
@Primary
@ConfigurationProperties(prefix = "spring.cloud.stream.pulsar.binder")
public class PulsarBinderProperties extends PulsarBinderConfigurationProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public PulsarBinderProperties() {
    }
}
