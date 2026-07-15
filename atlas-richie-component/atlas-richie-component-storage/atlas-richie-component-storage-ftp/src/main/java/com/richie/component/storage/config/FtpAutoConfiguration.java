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
package com.richie.component.storage.config;

import com.richie.component.storage.config.StorageEngineProvider;
import com.richie.component.storage.pool.FtpClientPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class FtpAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "platform.component.storage.ftp", name = "enable", havingValue = "true")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public FtpClientPool ftpClientPool(StorageProperties properties) {
        return new FtpClientPool(properties.getFtp());
    }

    /**
     * FTP 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider ftpStorageEngineProvider() {
        return new FtpStorageEngineProvider();
    }

}
