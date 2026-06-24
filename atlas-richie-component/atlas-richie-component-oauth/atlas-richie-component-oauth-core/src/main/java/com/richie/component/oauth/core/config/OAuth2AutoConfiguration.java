package com.richie.component.oauth.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * OAuth 2.1 自动装配
 * <p>
 * 通过条件装配启用组件，配置前缀 {@code platform.component.oauth}
 *
 * @author richie696
 * @since 2026-06-12
 */
@AutoConfiguration
@EnableConfigurationProperties(OAuth2Properties.class)
@ComponentScan("com.richie.component.oauth.core")
@ConditionalOnProperty(prefix = "platform.component.oauth", name = "enabled", havingValue = "true")
public class OAuth2AutoConfiguration {
}
