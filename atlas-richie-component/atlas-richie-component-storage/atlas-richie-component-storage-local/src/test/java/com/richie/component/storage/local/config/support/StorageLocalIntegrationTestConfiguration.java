package com.richie.component.storage.local.config.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.local.config.LocalCacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.liquibase.config.LiquibaseAutoConfiguration;
import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.config.StorageProperties;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@ComponentScan(basePackageClasses = {
        GlobalCache.class,
        com.richie.component.storage.local.core.impl.LocalStorageEngine.class,
        com.richie.component.storage.local.config.CacheConfigurationChecker.class
})
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        LocalCacheAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class,
        LiquibaseAutoConfiguration.class
})
public class StorageLocalIntegrationTestConfiguration {

    @Bean
    LocalConfig localConfig(StorageProperties properties) {
        return properties.getLocal();
    }
}
