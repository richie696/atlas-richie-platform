package com.richie.component.threadpool.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.dynamictp.common.entity.TpExecutorProps;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

/**
 * Undertow WebServer 线程池的 Dynamic-TP 配置属性（前缀：spring.dynamic.tp.undertow-tp）。
 *
 * @author richie696
 * @since 2023-10-24
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Primary
@ConfigurationProperties(prefix = "spring.dynamic.tp.undertow-tp")
public class UndertowProps extends TpExecutorProps {
}
