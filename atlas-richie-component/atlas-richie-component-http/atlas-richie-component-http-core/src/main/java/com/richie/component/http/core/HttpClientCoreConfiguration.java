package com.richie.component.http.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * HTTP Core 自动配置入口。
 * <p>
 * 该配置负责注册 {@link HttpCoreProperties}，供各个 Provider 的自动装配共享使用。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(HttpCoreProperties.class)
public class HttpClientCoreConfiguration {
}
