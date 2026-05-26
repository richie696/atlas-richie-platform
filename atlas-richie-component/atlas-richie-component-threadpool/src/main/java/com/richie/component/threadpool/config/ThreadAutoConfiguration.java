package com.richie.component.threadpool.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 线程池组件自动配置，注册 Dynamic-TP 与自定义异步线程池所需的配置属性 Bean。
 *
 * @author richie696
 * @since 2023-10-24
 */
@Configuration
@EnableConfigurationProperties({
        TomcatProps.class,
        JettyProps.class,
        UndertowProps.class,
        ZookeeperProps.class,
        EtcdProps.class,
})
public class ThreadAutoConfiguration {
}
