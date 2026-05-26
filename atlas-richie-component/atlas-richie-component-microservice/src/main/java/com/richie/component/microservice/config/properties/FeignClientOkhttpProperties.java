package com.richie.component.microservice.config.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.support.FeignHttpClientProperties;
import org.springframework.context.annotation.Primary;

/**
 * Feign客户端Okhttp配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-13 10:53:00
 */
@Data
@Primary
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "spring.cloud.openfeign.httpclient")
public class FeignClientOkhttpProperties extends FeignHttpClientProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public FeignClientOkhttpProperties() {
    }

    /** OkHttp 扩展配置（读/写/连接/调用超时、日志级别、缓存、SSL 等） */
    private OkhttpExtension okHttp = new OkhttpExtension();

}
