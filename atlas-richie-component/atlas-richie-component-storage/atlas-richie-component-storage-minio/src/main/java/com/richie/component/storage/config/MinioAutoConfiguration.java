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

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.core.StorageEngineProvider;
import io.minio.MinioAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 文件存储自动配置类
 * <p>
 * 工厂方法仅做客户端构造，不执行任何网络 I/O；桶的自动创建与前缀读写校验
 * 移至 {@code MinioStorageEngine} 的 {@code @PostConstruct} 阶段，
 * 该阶段异常会被降级为告警而不会阻塞 Spring 上下文启动。
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class MinioAutoConfiguration {

    /**
     * Minio 异步客户端。
     * <p>
     * 采用 {@code prototype} 作用域以保证调用方每次获取的是独立实例，
     * 避免客户端在多线程环境下的共享状态问题；工厂方法仅做客户端构造，
     * 任何网络操作（桶探测、对象读写校验）均在后续生命周期阶段执行。
     *
     * @param properties 存储配置
     * @return 配置完成的 Minio 异步客户端
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "minio")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public MinioAsyncClient minioAsyncClient(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        return MinioAsyncClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKeyId(), config.getAccessKeySecret())
                .region(config.getRegion())
                .build();
    }

    /**
     * MinIO 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider minioStorageEngineProvider() {
        return new MinioStorageEngineProvider();
    }

}
