package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.TosStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.volcengine.tos.TOSClientConfiguration;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.credential.StaticCredentialsProvider;
import com.volcengine.tos.transport.TransportConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TosStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.VOLCENGINE_TOS;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        TransportConfig transportConfig = TransportConfig.builder()
                .connectTimeoutMills(10000)
                .build();
        TOSClientConfiguration configuration = TOSClientConfiguration.builder()
                .transportConfig(transportConfig)
                .region(config.getRegion())
                .endpoint(config.getEndpoint())
                .credentialsProvider(new StaticCredentialsProvider(config.getAccessKeyId(), config.getAccessKeySecret()))
                .build();
        TOSV2 tos = new TOSV2ClientBuilder().build(configuration);
        TosStorageEngine engine = new TosStorageEngine(properties, null);
        engine.setClientOverride(tos);
        return engine;
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return TosStorageEngine.class.isAssignableFrom(engineClass);
    }

    @Override
    public void destroy(StorageEngine engine) {
        log.info("TOS 引擎已销毁");
    }

    @Override
    public void validate(StorageProperties properties) {
        ObjectConfig c = properties.getObject();
        ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
        ConfigValidation.requireNonBlank(c.getEndpoint(), "endpoint");
        ConfigValidation.requireNonBlank(c.getRegion(), "region");
        ConfigValidation.requireNonBlank(c.getAccessKeyId(), "accessKeyId");
        ConfigValidation.requireNonBlank(c.getAccessKeySecret(), "accessKeySecret");
        ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
    }
}
