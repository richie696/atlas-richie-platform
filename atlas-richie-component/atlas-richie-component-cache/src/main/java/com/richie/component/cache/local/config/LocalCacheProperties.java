package com.richie.component.cache.local.config;

import com.richie.context.utils.data.Collections;
import com.richie.component.cache.local.enums.CacheProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.util.Set;

/**
 * 本地缓存配置文件
 *
 * @author richie696
 * @version 1.0
 * @since 2023-12-28 17:18:32
 */
@Data
@ConfigurationProperties(prefix = "spring.data.local")
public class LocalCacheProperties implements Serializable {

    /** 默认构造函数，供配置绑定使用。 */
    public LocalCacheProperties() {
    }

    /** 缓存提供者（如 EHCACHE、CAFFEINE、CACHE2K） */
    private CacheProvider provider = CacheProvider.EHCACHE;

    /**
     * 多级缓存配置
     */
    private Set<CacheDefinition> cacheDefinitions = Collections.setOf();

}
