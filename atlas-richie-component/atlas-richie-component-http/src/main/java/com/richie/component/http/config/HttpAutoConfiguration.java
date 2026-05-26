package com.richie.component.http.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * HTTP客户端配置
 * 支持 OkHttp 和 HttpClient5 两种实现
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-10 18:24:44
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.http")
@EnableConfigurationProperties({HttpProperties.class})
@Import({
        OkhttpConfiguration.class,
        HttpClient5Configuration.class
})
public class HttpAutoConfiguration {

    /**
     * 构造函数
     */
    public HttpAutoConfiguration() {
        log.info("初始化HTTP服务配置模块");
    }

}
