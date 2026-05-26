package com.richie.component.cache.local.config;

import com.richie.component.cache.enums.L2CachingRegion;
import com.richie.component.cache.local.enums.ExpiryPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.*;
import javax.cache.spi.CachingProvider;
import java.util.concurrent.TimeUnit;

import static com.richie.component.cache.enums.L2CachingRegion.ACCESS_LOG;

/**
 * 本地缓存自动配置类
 *
 * @author richie696
 * @version 1.1
 * @since 2020/07/02
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LocalCacheProperties.class})
public class LocalCacheAutoConfiguration {

    /** 默认构造函数，供 Spring 实例化使用。 */
    public LocalCacheAutoConfiguration() {
    }

    /**
     * 注册 JSR-107 CacheManager Bean，并初始化各缓存区域（含全局缓存与访问日志缓存）。
     *
     * @param properties 本地缓存配置
     * @return CacheManager 实例
     */
    @Bean("cacheManagerJsr107")
    public CacheManager cacheManager(LocalCacheProperties properties) {
        CachingProvider provider = Caching.getCachingProvider(properties.getProvider().getCachingProvider());
        CacheManager cacheManager = provider.getCacheManager();
        properties.getCacheDefinitions().forEach(cacheDefinition -> {
            MutableConfiguration<String, Object> configuration = getMutableConfiguration(cacheDefinition);
            cacheManager.createCache(cacheDefinition.getName(), configuration);
        });
        // 初始化全局缓存
        initialGlobalCacheL2Caching(cacheManager);

        // 访问日志专用
        cacheManager.createCache(ACCESS_LOG.getCache(), new MutableConfiguration<String, Object>()
                .setTypes(String.class, Object.class)
                .setStoreByValue(false)
                .setStatisticsEnabled(false)
                .setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(Duration.FIVE_MINUTES)));
        return cacheManager;
    }

    private static MutableConfiguration<String, Object> getMutableConfiguration(CacheDefinition cacheDefinition) {
        MutableConfiguration<String, Object> configuration = new MutableConfiguration<String, Object>()
                .setTypes(String.class, Object.class)
                .setStoreByValue(cacheDefinition.isStoreByValue())
                .setStatisticsEnabled(cacheDefinition.isStatisticsEnabled());
        Duration expiryDuration = new Duration(cacheDefinition.getExpiryUnit(), cacheDefinition.getExpiry());
        switch (cacheDefinition.getExpiryPolicy()) {
            case CREATED -> configuration.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(expiryDuration));
            case ACCESSED -> configuration.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(expiryDuration));
            case ETERNAL -> configuration.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
            case MODIFIED -> configuration.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(expiryDuration));
            case TOUCHED -> configuration.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(expiryDuration));
            default ->
                    throw new IllegalArgumentException("Unknown expiry policy: %s".formatted(cacheDefinition.getExpiryPolicy()));
        }
        if (cacheDefinition.isReadThrough()) {
            configuration.setReadThrough(true);
            configuration.setCacheLoaderFactory(FactoryBuilder.factoryOf(cacheDefinition.getCacheLoaderClassName()));
        }
        if (cacheDefinition.isWriteThrough()) {
            configuration.setWriteThrough(true);
            configuration.setCacheWriterFactory(FactoryBuilder.factoryOf(cacheDefinition.getCacheWriterClassName()));
        }
        return configuration;
    }

    private CacheDefinition getGlobalCacheDefinition() {
        return new CacheDefinition()
                .setName(L2CachingRegion.GLOBAL_CACHE.getCache())
                .setExpiry(1)
                .setExpiryUnit(TimeUnit.HOURS)
                .setExpiryPolicy(ExpiryPolicy.ACCESSED)
                .setStoreByValue(false)
                .setStatisticsEnabled(true)
                .setReadThrough(false)
                .setWriteThrough(false);
    }

    private void initialGlobalCacheL2Caching(CacheManager cacheManager) {
        CacheDefinition cacheDefinition = getGlobalCacheDefinition();
        MutableConfiguration<String, Object> globalCacheConfiguration = getMutableConfiguration(cacheDefinition);
        cacheManager.createCache(L2CachingRegion.GLOBAL_CACHE.getCache(), globalCacheConfiguration);
    }

}
