package com.richie.component.threadpool.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.dynamictp.common.properties.DtpProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

/**
 * Dynamic-TP 与 Zookeeper 集成的配置属性（前缀：spring.dynamic.tp.zookeeper）。
 *
 * @author richie696
 * @since 2023-10-24
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Primary
@ConfigurationProperties(prefix = "spring.dynamic.tp.zookeeper")
public class ZookeeperProps extends DtpProperties.Zookeeper {
}
