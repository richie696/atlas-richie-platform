/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.OssStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OssStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.ALIYUN_OSS;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        DefaultCredentialProvider credentialProvider = CredentialsProviderFactory
                .newDefaultCredentialProvider(config.getAccessKeyId(), config.getAccessKeySecret());
        OSS ossClient = new OSSClientBuilder().build(config.getEndpoint(), credentialProvider);
        if (config.isAutoCreateBucket()) {
            ossClient.createBucket(config.getBucketName());
        }
        OssStorageEngine engine = new OssStorageEngine(properties, null);
        engine.setClientOverride(ossClient);
        return engine;
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return OssStorageEngine.class.isAssignableFrom(engineClass);
    }

    @Override
    public void destroy(StorageEngine engine) {
        log.info("OSS 引擎已销毁");
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
