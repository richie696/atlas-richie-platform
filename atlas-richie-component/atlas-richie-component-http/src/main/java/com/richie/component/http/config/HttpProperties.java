package com.richie.component.http.config;

import com.richie.component.http.bean.HttpProvider;
import lombok.Data;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * HTTP客户端配置文件
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-22 23:53:50
 */
@Data
@ConfigurationProperties(prefix = "platform.component.http")
public class HttpProperties {

    /**
     * HTTP客户端提供商
     */
    private HttpProvider provider = HttpProvider.OKHTTP;

    /**
     * 读取超时时间（默认：5秒）
     */
    private Integer readTimeout = 5;
    /**
     * 读取超时时间单位（默认：秒）
     */
    private TimeUnit readTimeoutTimeUnit = TimeUnit.SECONDS;
    /**
     * 写入超时时间（默认：5秒）
     */
    private Integer writeTimeout = 5;
    /**
     * 写入超时时间单位（默认：秒）
     */
    private TimeUnit writeTimeoutTimeUnit = TimeUnit.SECONDS;
    /**
     * 连接超时时间（默认：5秒）
     */
    private Integer connectTimeout = 5;
    /**
     * 连接超时时间单位（默认：秒）
     */
    private TimeUnit connectTimeoutTimeUnit = TimeUnit.SECONDS;
    /**
     * 调用超时时间（默认：15秒）
     */
    private Integer callTimeoutTimeout = 15;
    /**
     * 调用超时时间单位（默认：秒）
     */
    private TimeUnit callTimeoutTimeUnit = TimeUnit.SECONDS;
    /**
     * HTTP访问日志级别（默认：BODY，可用值：NONE、BASIC、HEADERS、BODY）
     */
    private HttpLoggingInterceptor.Level level = HttpLoggingInterceptor.Level.BODY;
    /**
     * 是否启用HTTP缓存（默认：false）
     */
    private Boolean enableCache = false;
    /**
     * HTTP缓存路径（默认：/opt/okhttp3/cache/）
     */
    private String cachePath = "/opt/okhttp3/cache/";
    /**
     * HTTP缓存大小（默认：100M，单位：MB）
     */
    private Integer cacheSize = 100;
    /**
     * 整体实例最大并发请求数（默认值：200）
     */
    private Integer maxRequests = 250;
    /**
     * 同一实例默认最大并发请求数（默认值：25）
     */
    private Integer maxRequestsPerHost = 25;
    /**
     * 连接保持时间（默认：5分钟）
     */
    private Long keepAliveDuration = 5L;
    /**
     * 连接保持时间单位（默认：分钟）
     */
    private TimeUnit keepAliveTimeUnit = TimeUnit.MINUTES;

    /**
     * 是否跳过所有证书校验（仅用于联调/测试，生产请关闭）
     */
    private Boolean insecureTrustAll = false;
    /**
     * 自定义信任库路径（JKS/PKCS12），为空则使用系统默认 CA
     */
    private String truststorePath;
    /**
     * 自定义信任库密码
     */
    private String truststorePassword;
    /**
     * 自定义信任库类型，默认 JKS，可取：JKS、PKCS12
     */
    private String truststoreType = "JKS";
    /**
     * 是否启用主机名校验，默认开启
     */
    private Boolean hostnameVerification = true;

}
