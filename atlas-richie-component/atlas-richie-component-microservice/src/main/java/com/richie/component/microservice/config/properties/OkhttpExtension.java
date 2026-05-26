package com.richie.component.microservice.config.properties;

import lombok.Data;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Okhttp客户端扩展配置信息
 * <p>
 * Spring Cloud 2025.1.0: FeignHttpClientProperties.OkHttp 已被移除，改为独立配置类
 *
 * @author richie696
 * @version 2.0
 * @since 2023-09-05 10:56:09
 */
@Data
@Primary
@ConfigurationProperties(prefix = "spring.cloud.openfeign.httpclient.okhttp")
public class OkhttpExtension {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public OkhttpExtension() {
    }

    /**
     * 读超时时长（单位：秒）
     * <p>
     * 配置建议：当服务器负载高的时候，可以考虑增加 read-timeout 的时间
     */
    private Duration readTimeout = Duration.ofSeconds(60);
    /**
     * 写超时时长（单位：秒）
     * <p>
     * 配置建议：当服务中的大请求体较多时，可以考虑增加 write-timeout 的时间
     */
    private Duration writeTimeout = Duration.ofSeconds(10);
    /**
     * 连接超时时长（单位：秒）
     * <p>
     * 配置建议：当网络抖动严重时，可以考虑增加 connect-timeout 的时间
     */
    private Duration connectTimeout = Duration.ofSeconds(3);
    /**
     * 调用超时时长（单位：秒）
     * <p>
     * call-timeout ≥ connect-timeout + write-timeout + read-timeout + 其他处理时间
     * <p>
     * OkHttp 的实际行为：
     * <ul>
     *     <li>如果 call-timeout 小于其他超时之和，call-timeout 会先触发</li>
     *     <li>如果 call-timeout 大于其他超时之和，其他超时会先触发</li>
     *     <li>最佳实践：call-timeout 应该略大于其他超时之和</li>
     * </ul>
     * <p>
     * 设置建议：
     * <ul>
     *     <li>网络抖动：增加 connect-timeout</li>
     *     <li>服务器负载高：增加 read-timeout</li>
     *     <li>大请求体：增加 write-timeout</li>
     * </ul>
     * <p>
     * 例：高频微服务调用
     * <ul>
     *     <li>connect-timeout: 5s</li>
     *     <li>write-timeout: 10s</li>
     *     <li>read-timeout: 15s</li>
     *     <li>call-timeout: 35s  # 5+10+15+5=35</li>
     * </ul>
     * <p>
     * 复杂业务处理：
     * <ul>
     *     <li>connect-timeout: 8s</li>
     *     <li>write-timeout: 15s</li>
     *     <li>read-timeout: 20s</li>
     *     <li>其他处理时间: ≈7s (包括：DNS解析、SSL握手、HTTP协议处理等)</li>
     *     <li>call-timeout: 50s (8+15+20+7=50)</li>
     * </ul>
     */
    private Duration callTimeout = Duration.ofSeconds(30);
    /**
     * HTTP访问日志级别（默认：BODY，可用值：NONE、BASIC、HEADERS、BODY）
     */
    private HttpLoggingInterceptor.Level level = HttpLoggingInterceptor.Level.BODY;
    /**
     * 是否启用HTTP缓存（默认：false）
     */
    private Boolean enableCache = false;
    /**
     * HTTP缓存路径（默认：/tmp/okhttp3/cache/）
     */
    private String cachePath = "/tmp/okhttp3/cache/";
    /**
     * HTTP缓存大小（默认：100M，单位：MB）
     */
    private Integer cacheSize = 100;
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
