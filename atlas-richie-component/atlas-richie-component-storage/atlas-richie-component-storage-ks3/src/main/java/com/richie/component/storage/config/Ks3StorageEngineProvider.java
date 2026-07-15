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

import com.ksyun.ks3.http.HttpClientConfig;
import com.ksyun.ks3.service.Ks3;
import com.ksyun.ks3.service.Ks3Client;
import com.ksyun.ks3.service.Ks3ClientConfig;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.config.StorageEngineProvider;
import com.richie.component.storage.core.impl.Ks3StorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Ks3StorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.KSYUN_KS3;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        Ks3ClientConfig ks3ClientConfig = new Ks3ClientConfig();
        ks3ClientConfig.setEndpoint(config.getEndpoint());
        ks3ClientConfig.setDomainMode(false);
        ks3ClientConfig.setProtocol(Ks3ClientConfig.PROTOCOL.https);
        ks3ClientConfig.setPathStyleAccess(false);
        ks3ClientConfig.setHttpClientConfig(new HttpClientConfig());
        Ks3 ks3Client = new Ks3Client(config.getAccessKeyId(), config.getAccessKeySecret(), ks3ClientConfig);
        if (config.isAutoCreateBucket()) {
            ks3Client.createBucket(config.getBucketName());
        }
        Ks3StorageEngine engine = new Ks3StorageEngine(properties, null);
        engine.setClientOverride(ks3Client);
        return engine;
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return Ks3StorageEngine.class.isAssignableFrom(engineClass);
    }

    @Override
    public void destroy(StorageEngine engine) {
        log.info("KS3 引擎已销毁");
    }

    @Override
    public void validate(StorageProperties properties) {
        ObjectConfig c = properties.getObject();
        ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
        ConfigValidation.requireNonBlank(c.getEndpoint(), "endpoint");
        ConfigValidation.requireNonBlank(c.getAccessKeyId(), "accessKeyId");
        ConfigValidation.requireNonBlank(c.getAccessKeySecret(), "accessKeySecret");
        ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
    }
}
