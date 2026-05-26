package com.richie.component.messaging.pulsar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Pulsar 消息组件配置类。
 * <p>
 * 扫描消息组件与 Spring Cloud Stream Pulsar Binder 包，并启用 Pulsar 相关配置属性。
 *
 * @author richie696
 * @version 1.1
 * @since 2020/07/02
 */
@Slf4j
@Configuration
@ComponentScan({
        "com.richie.component.messaging",
        "org.springframework.cloud.stream.binder.pulsar",
})
@EnableConfigurationProperties({PulsarBinderProperties.class})
public class PulsarConfiguration {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public PulsarConfiguration() {
    }
}
