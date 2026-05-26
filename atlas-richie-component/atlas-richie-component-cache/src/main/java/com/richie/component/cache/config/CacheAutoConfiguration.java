package com.richie.component.cache.config;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Redis客户端配置类
 *
 * @author richie696
 * @version 1.1
 * @since 2020/07/02
 */
@Slf4j
@NoArgsConstructor
@Configuration
@ComponentScan("com.richie.component.cache")
@EnableConfigurationProperties({CacheProperties.class})
public class CacheAutoConfiguration {

}
