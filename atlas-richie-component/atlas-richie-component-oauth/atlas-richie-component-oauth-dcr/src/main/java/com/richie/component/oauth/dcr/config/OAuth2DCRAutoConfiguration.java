package com.richie.component.oauth.dcr.config;

import com.richie.component.oauth.core.config.OAuth2AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * OAuth 2.0 DCR 自动装配
 * <p>
 * 通过条件装配启用动态客户端注册组件，配置前缀 {@code platform.component.oauth-dcr}
 *
 * @author richie696
 * @since 2026-06-12
 */
@AutoConfiguration
@ComponentScan("com.richie.component.oauth.dcr")
@Import(OAuth2AutoConfiguration.class)
public class OAuth2DCRAutoConfiguration {
}
