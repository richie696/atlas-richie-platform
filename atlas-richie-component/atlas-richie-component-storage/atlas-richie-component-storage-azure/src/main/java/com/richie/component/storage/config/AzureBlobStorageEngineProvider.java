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
