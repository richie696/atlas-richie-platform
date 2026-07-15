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

import com.ksyun.ks3.dto.HeadBucketResult;
import com.ksyun.ks3.exception.Ks3ClientException;
import com.ksyun.ks3.http.HttpClientConfig;
import com.ksyun.ks3.service.Ks3;
import com.ksyun.ks3.service.Ks3Client;
import com.ksyun.ks3.service.Ks3ClientConfig;
import com.ksyun.ks3.service.request.GetObjectRequest;
import com.ksyun.ks3.service.request.PutObjectRequest;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageEngineProvider;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
public class Ks3AutoConfiguration {

    /**
     * 金山云KS3客户端
     *
     * @param properties 存储配置
     * @return 返回金山云KS3客户端
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "ksyun_ks3")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public Ks3 ks3Client(StorageProperties properties) throws StorageException {
        ObjectConfig config = properties.getObject();
        Ks3ClientConfig ks3ClientConfig = new Ks3ClientConfig();
        ks3ClientConfig.setEndpoint(config.getEndpoint());
        ks3ClientConfig.setDomainMode(false);
        ks3ClientConfig.setProtocol(Ks3ClientConfig.PROTOCOL.https);
        ks3ClientConfig.setPathStyleAccess(false);
        HttpClientConfig httpClientConfig = new HttpClientConfig();
        ks3ClientConfig.setHttpClientConfig(httpClientConfig);
        Ks3 ks3Client = new Ks3Client(config.getAccessKeyId(), config.getAccessKeySecret(), ks3ClientConfig);
        if (!config.isAutoCreateBucket()) {
            verifyKs3Prefix(ks3Client, config);
            return ks3Client;
        }
        HeadBucketResult bucketResult = ks3Client.headBucket(config.getBucketName());
        switch (bucketResult.getStatusCode()) {
            case 200 -> {
            }
            case 404 -> ks3Client.createBucket(config.getBucketName());
            default -> throw new StorageException("当前桶无访问权限");
        }
        return ks3Client;
    }

    private void verifyKs3Prefix(Ks3 ks3Client, ObjectConfig config) throws StorageException {
        String bucket = config.getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        try {
            ks3Client.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(bytes)));
            var objectResult = ks3Client.getObject(new GetObjectRequest(bucket, key));
            try (InputStream in = objectResult.getObject().getObjectContent()) {
                in.readAllBytes();
            }
        } catch (Ks3ClientException | IOException e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                ks3Client.deleteObject(bucket, key);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * KS3 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider ks3StorageEngineProvider() {
        return new Ks3StorageEngineProvider();
    }

}
