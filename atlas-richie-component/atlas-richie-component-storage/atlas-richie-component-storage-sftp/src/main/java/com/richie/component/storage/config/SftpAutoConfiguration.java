/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.config;

import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.pool.SftpSessionPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.core.CoreModuleProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class SftpAutoConfiguration {

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "platform.component.storage.sftp", name = "enable", havingValue = "true")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public SshClient sshClient() {
        var client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((_, _, _) -> true);
        CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ofMinutes(5));
        client.start();
        log.info("SSHD client started");
        return client;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "platform.component.storage.sftp", name = "enable", havingValue = "true")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public SftpSessionPool sftpSessionPool(SshClient sshClient, StorageProperties properties) {
        return new SftpSessionPool(sshClient, properties.getSftp());
    }

    /**
     * SFTP 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider sftpStorageEngineProvider() {
        return new SftpStorageEngineProvider();
    }

}
