package com.richie.component.cache.config;

import com.richie.component.cache.enums.CacheProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 缓存属性配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2026-02-07 19:46:01
 */
@Data
@ConfigurationProperties(prefix = "platform.cache")
public class CacheProperties {

    /**
     * 缓存提供者（可选值：REDIS【默认】）
     */
    private CacheProvider cacheProvider = CacheProvider.REDIS;

    /**
     * 布隆过滤器配置
     */
    private BloomFilterConfig bloomFilter = new BloomFilterConfig();

}
