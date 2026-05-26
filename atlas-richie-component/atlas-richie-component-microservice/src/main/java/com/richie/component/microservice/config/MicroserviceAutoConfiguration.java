package com.richie.component.microservice.config;

import com.richie.component.microservice.config.openfeign.FeignClientAutoConfiguration;
import com.richie.component.microservice.config.properties.FeignClientOkhttpProperties;
import com.richie.component.microservice.config.restclient.RestClientAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * OpenFeign OkHttp 自动配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:32:00
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.microservice")
@EnableConfigurationProperties({FeignClientOkhttpProperties.class})
@Import({
        FeignClientAutoConfiguration.class,
        RestClientAutoConfiguration.class
})
public class MicroserviceAutoConfiguration {

    /**
     * 默认构造函数（供 Spring 使用），并打印初始化日志。
     */
    public MicroserviceAutoConfiguration() {
        log.info("初始化微服务配置模块");
    }

}
