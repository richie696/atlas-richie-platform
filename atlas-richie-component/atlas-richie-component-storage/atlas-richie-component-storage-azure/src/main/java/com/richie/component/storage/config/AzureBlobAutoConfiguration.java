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

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;

/**
 * 文件存储自动配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
@NoArgsConstructor
public class AzureBlobAutoConfiguration {

    /**
     * 微软Azure Blob容器客户端
     *
     * @param properties 存储配置
     * @return 返回 Azure Blob 容器客户端
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "azure_blob")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public BlobContainerClient blobContainerClient(StorageProperties properties) throws StorageException {
        ObjectConfig config = properties.getObject();

        var blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(config.getAccessKeySecret())
                .endpoint(config.getEndpoint())
                .buildClient();
        BlobContainerClient container = config.isAutoCreateBucket()
                ? blobServiceClient.createBlobContainerIfNotExists(config.getBucketName())
                : blobServiceClient.getBlobContainerClient(config.getBucketName());
        if (!config.isAutoCreateBucket()) {
            verifyAzurePrefix(container, config);
        }
        return container;
    }

    private void verifyAzurePrefix(BlobContainerClient container, ObjectConfig config) throws StorageException {
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        var blobClient = container.getBlobClient(key);
        try {
            try (var bis = new ByteArrayInputStream(bytes)) {
                blobClient.upload(bis, bytes.length);
            }
            try (var is = blobClient.openInputStream()) {
                is.readAllBytes();
            }
        } catch (Exception e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                blobClient.deleteIfExists();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Azure Blob 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider azureBlobStorageEngineProvider() {
        return new AzureBlobStorageEngineProvider();
    }

}
