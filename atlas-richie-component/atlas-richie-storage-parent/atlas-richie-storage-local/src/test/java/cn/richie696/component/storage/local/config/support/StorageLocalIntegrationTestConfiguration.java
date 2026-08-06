/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.storage.local.config.support;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.config.CacheAutoConfiguration;
import cn.richie696.component.cache.local.config.LocalCacheAutoConfiguration;
import cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import cn.richie696.component.liquibase.config.LiquibaseAutoConfiguration;
import cn.richie696.component.storage.bean.LocalConfig;
import cn.richie696.component.storage.config.StorageProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
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
        cn.richie696.component.storage.local.core.impl.LocalStorageEngine.class,
        cn.richie696.component.storage.local.config.CacheConfigurationChecker.class
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
