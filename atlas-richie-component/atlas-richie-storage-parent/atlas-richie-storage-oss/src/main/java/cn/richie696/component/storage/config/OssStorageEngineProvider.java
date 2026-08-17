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
package cn.richie696.component.storage.config;

import cn.richie696.component.storage.bean.ObjectConfig;
import cn.richie696.component.storage.core.StorageEngine;
import cn.richie696.component.storage.core.impl.OssStorageEngine;
import cn.richie696.component.storage.enums.StorageEngineEnum;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Set;

import static cn.richie696.component.storage.enums.AclTypeEnum.*;
import static cn.richie696.component.storage.enums.StorageTypeEnum.*;

@Slf4j
public class OssStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.ALIYUN_OSS;
    }

    @Override
    public Set<cn.richie696.component.storage.enums.StorageTypeEnum> supportedStorageTypes() {
        return EnumSet.of(STANDARD, STANDARD_IA, ARCHIVE, COLD_ARCHIVE, DEEP_COLD_ARCHIVE);
    }

    @Override
    public Set<cn.richie696.component.storage.enums.AclTypeEnum> supportedAclTypes() {
        return EnumSet.of(PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE, AUTHENTICATED_READ,
                BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL);
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
