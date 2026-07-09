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

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.AzureBlobStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AzureBlobStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.AZURE_BLOB;
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return AzureBlobStorageEngine.class.isAssignableFrom(engineClass);
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        var blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(config.getAccessKeySecret())
                .endpoint(config.getEndpoint())
                .buildClient();
        BlobContainerClient container = config.isAutoCreateBucket()
                ? blobServiceClient.createBlobContainerIfNotExists(config.getBucketName())
                : blobServiceClient.getBlobContainerClient(config.getBucketName());
        AzureBlobStorageEngine engine = new AzureBlobStorageEngine(properties);
        engine.setClientOverride(container);
        return engine;
    }

    @Override
    public void destroy(StorageEngine engine) {
        log.info("Azure Blob 引擎已销毁");
    }

    @Override
    public void validate(StorageProperties properties) {
        ObjectConfig c = properties.getObject();
        ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
        ConfigValidation.requireNonBlank(c.getEndpoint(), "endpoint");
        ConfigValidation.requireNonBlank(c.getAccessKeySecret(), "accessKeySecret (connectionString)");
        ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
    }
}
