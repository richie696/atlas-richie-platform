package com.richie.component.mqtt.config;

import com.richie.contract.gateway.config.DeployConfig;
import com.richie.component.mqtt.canary.CanaryInstanceManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 客户端自动配置类
 * <p>
 * 负责MQTT客户端的自动配置，包括Bean创建、属性绑定等。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-23 14:50:44
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.mqtt")
@EnableConfigurationProperties({MqttClientProperties.class, ServerInfo.class, ServerInfo.Ssl.class})
public class MqttClientAutoConfiguration {

    /**
     * 创建灰度实例管理器 Bean
     * <p>
     * 统一使用 Gateway 的 DeployConfig 作为全局灰度总控开关
     * <p>
     * 条件：
     * 1. DiscoveryClient 存在（服务发现可用）
     * 2. DeployConfig 存在（Gateway 灰度配置可用）
     *
     * @param discoveryClient 服务发现客户端
     * @param applicationName 应用名称
     * @param deployConfig 部署配置
     * @return 灰度实例管理器实例
     */
    @Bean
    @ConditionalOnBean({DiscoveryClient.class, DeployConfig.class})
    public CanaryInstanceManager canaryInstanceManager(
            DiscoveryClient discoveryClient,
            @Value("${spring.application.name:unknown}") String applicationName,
            DeployConfig deployConfig) {
        log.info("MQTT canary instance manager enabled for application: {}, using Gateway DeployConfig as global control", applicationName);
        return new CanaryInstanceManager(discoveryClient, applicationName, deployConfig);
    }

}
