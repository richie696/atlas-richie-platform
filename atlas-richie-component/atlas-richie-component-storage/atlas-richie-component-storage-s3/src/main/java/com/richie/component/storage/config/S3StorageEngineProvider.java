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

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.S3StorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
public class S3StorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.AWS_S3;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig config = properties.getObject();
        URI endpointUri = buildEndpointUri(config.getEndpoint());
        S3Client s3Client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getAccessKeySecret())))
                .build();
        S3StorageEngine engine = new S3StorageEngine(properties, null);
        engine.setClientOverride(s3Client);
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getAccessKeySecret())))
                .build();
        engine.setS3Presigner(presigner);
        return engine;
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return S3StorageEngine.class.isAssignableFrom(engineClass);
    }

    @Override
    public void destroy(StorageEngine engine) {
        log.info("S3 引擎已销毁");
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

    private static URI buildEndpointUri(String endpoint) {
        String ep = endpoint;
        if (!ep.startsWith("http://") && !ep.startsWith("https://")) {
            ep = "https://" + ep;
        }
        return URI.create(ep);
    }
}
