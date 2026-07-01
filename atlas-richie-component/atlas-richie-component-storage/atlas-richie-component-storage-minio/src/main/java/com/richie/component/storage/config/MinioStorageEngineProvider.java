package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.MinioStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import io.minio.MinioAsyncClient;
import lombok.extern.slf4j.Slf4j;

/**
 * MinIO 存储引擎 Provider
 * <p>
 * 负责从 StorageProperties 创建 MinioStorageEngine 实例，
 * 并处理手动模式下的桶探测和引擎销毁。
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
public class MinioStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.MINIO;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        MinioAsyncClient client = MinioAsyncClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKeyId(), config.getAccessKeySecret())
                .region(config.getRegion())
                .build();
        return new MinioStorageEngine(properties, client);
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return MinioStorageEngine.class.isAssignableFrom(engineClass);
    }

    @Override
    public void afterPropertiesSet(StorageEngine engine) {
        if (engine instanceof MinioStorageEngine minioEngine) {
            minioEngine.initializeBucket();
        }
    }

    @Override
    public void destroy(StorageEngine engine) {
        // MinIO 无连接池，客户端引用置空后由 GC 回收
        log.info("MinIO 引擎已销毁");
    }

    @Override
    public void validate(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        ConfigValidation.requireNonNull(config, "对象存储配置 (object)");
        ConfigValidation.requireNonBlank(config.getEndpoint(), "endpoint");
        ConfigValidation.requireNonBlank(config.getAccessKeyId(), "accessKeyId");
        ConfigValidation.requireNonBlank(config.getAccessKeySecret(), "accessKeySecret");
        ConfigValidation.requireNonBlank(config.getBucketName(), "bucketName");
    }
}
