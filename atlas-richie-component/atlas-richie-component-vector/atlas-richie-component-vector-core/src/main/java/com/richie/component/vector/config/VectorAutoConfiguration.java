package com.richie.component.vector.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 向量数据库自动配置类
 */
@Configuration
@ComponentScan(basePackages = {"com.richie.component.vector"})
@EnableConfigurationProperties(VectorProperties.class)
public class VectorAutoConfiguration {

}
