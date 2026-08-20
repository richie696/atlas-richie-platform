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
import cn.richie696.component.storage.core.impl.CosStorageEngine;
import cn.richie696.component.storage.enums.StorageEngineEnum;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.HeadBucketRequest;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Set;

import static cn.richie696.component.storage.enums.AclTypeEnum.*;
import static cn.richie696.component.storage.enums.StorageTypeEnum.*;

@Slf4j
public class CosStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.TENCENT_COS;
    }

    @Override
    public Set<cn.richie696.component.storage.enums.StorageTypeEnum> supportedStorageTypes() {
        return EnumSet.of(STANDARD, STANDARD_IA, ARCHIVE, COLD_ARCHIVE, DEEP_COLD_ARCHIVE,
                INTELLIGENT_TIERING, MULTI_AZ_STANDARD, MULTI_AZ_STANDARD_IA,
                MULTI_AZ_ARCHIVE, MULTI_AZ_COLD_ARCHIVE, MULTI_AZ_DEEP_COLD_ARCHIVE,
                MULTI_AZ_INTELLIGENT_TIERING);
    }

    @Override
    public Set<cn.richie696.component.storage.enums.AclTypeEnum> supportedAclTypes() {
        return EnumSet.of(PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE, AUTHENTICATED_READ,
                BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL);
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        COSCredentials credentials = new BasicCOSCredentials(config.getAccessKeyId(), config.getAccessKeySecret());
        ClientConfig clientConfig = new ClientConfig(new Region(config.getRegion()));
        COSClient cosClient = new COSClient(credentials, clientConfig);
        if (config.isAutoCreateBucket()) {
            ensureBucket(cosClient, config.getBucketName());
        }
        CosStorageEngine engine = new CosStorageEngine(properties, null);
        engine.setClientOverride(cosClient);
        return engine;
    }

    /**
     * 确保 Bucket 可用。不能直接调用 createBucket：管理后台的“自动创建”开关
     * 表示“不存在时创建”，而不是每次启用 Provider 都重复创建。
     *
     * <p>先用当前凭据探测 Bucket。探测成功说明 Bucket 已存在且当前账号可访问，
     * 直接复用；只有明确返回 404 时才创建。其它状态（例如无权限）必须原样失败，
     * 避免把别的账号占用的同名 Bucket 当成本账号资源。</p>
     */
    private void ensureBucket(COSClient cosClient, String bucketName) {
        try {
            cosClient.headBucket(new HeadBucketRequest(bucketName));
            log.info("COS Bucket 已存在，跳过创建: {}", bucketName);
            return;
        } catch (CosServiceException error) {
            if (error.getStatusCode() != 404) {
                throw error;
            }
            log.info("COS Bucket 不存在，开始创建: {}", bucketName);
        }
        cosClient.createBucket(bucketName);
        log.info("COS Bucket 创建成功: {}", bucketName);
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return CosStorageEngine.class.isAssignableFrom(engineClass);
    }

    @Override
    public void destroy(StorageEngine engine) {
        if (engine instanceof CosStorageEngine cosStorageEngine) {
            cosStorageEngine.closeClient();
        }
        log.info("COS 引擎已销毁");
    }

    @Override
    public void validate(StorageProperties properties) {
        ObjectConfig c = properties.getObject();
        ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
        ConfigValidation.requireNonBlank(c.getRegion(), "region");
        ConfigValidation.requireNonBlank(c.getAccessKeyId(), "accessKeyId");
        ConfigValidation.requireNonBlank(c.getAccessKeySecret(), "accessKeySecret");
        ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
    }
}
