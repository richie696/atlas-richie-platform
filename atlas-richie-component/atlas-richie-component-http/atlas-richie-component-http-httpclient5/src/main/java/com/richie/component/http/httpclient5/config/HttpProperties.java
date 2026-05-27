package com.richie.component.http.httpclient5.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * HttpClient5 实现配置。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.http.httpclient5")
public class HttpProperties {

    /** 连接请求超时（默认：5，单位：秒）。从连接池获取连接的超时时间。 */
    private Integer connectionRequestTimeout = 5;
    private TimeUnit connectionRequestTimeoutTimeUnit = TimeUnit.SECONDS;

    /** 响应超时（默认：5，单位：秒）。等待服务端返回数据的超时。 */
    private Integer responseTimeout = 5;
    private TimeUnit responseTimeoutTimeUnit = TimeUnit.SECONDS;

    /** 连接管理器最大总连接数（默认：250）。 */
    private Integer maxTotal = 250;

    /** 每个 Route 最大连接数（默认：25）。 */
    private Integer defaultMaxPerRoute = 25;

}
