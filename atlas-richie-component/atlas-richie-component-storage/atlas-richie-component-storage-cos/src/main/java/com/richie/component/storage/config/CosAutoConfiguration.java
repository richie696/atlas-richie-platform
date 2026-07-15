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

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.HeadBucketRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageEngineProvider;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
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
public class CosAutoConfiguration {

    /**
     * 腾讯云 COS 客户端 Bean（当 engine=tencent_cos 时创建，桶不存在则自动创建）。
     *
     * @param properties 存储配置
     * @return 腾讯云 COS 客户端
     * @throws StorageException 桶无访问权限或创建/校验失败时抛出
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "tencent_cos")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public COSClient cosClient(StorageProperties properties) throws StorageException {
        ObjectConfig config = properties.getObject();
        COSCredentials credentials = new BasicCOSCredentials(config.getAccessKeyId(), config.getAccessKeySecret());
        ClientConfig clientConfig = new ClientConfig(new Region(config.getRegion()));
        COSClient cosClient = new COSClient(credentials, clientConfig);
        if (!config.isAutoCreateBucket()) {
            verifyCosPrefixAccess(cosClient, config);
            return cosClient;
        }
        int statusCode = HttpStatus.SC_OK;
        String errorMessage = "";
        try {
            cosClient.headBucket(new HeadBucketRequest(config.getBucketName()));
        } catch (CosServiceException e) {
            statusCode = e.getStatusCode();
            errorMessage = e.getMessage();
        } catch (CosClientException e) {
            statusCode = HttpStatus.SC_BAD_REQUEST;
            errorMessage = e.toString();
        }
        switch (statusCode) {
            case HttpStatus.SC_OK -> {
            }
            case HttpStatus.SC_NOT_FOUND -> cosClient.createBucket(config.getBucketName());
            case HttpStatus.SC_BAD_REQUEST -> throw new StorageException(errorMessage);
            default -> throw new StorageException("当前桶无访问权限");
        }
        return cosClient;
    }

    private void verifyCosPrefixAccess(COSClient cosClient, ObjectConfig config) throws StorageException {
        String bucket = config.getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(bytes.length);
        try {
            cosClient.putObject(bucket, key, new ByteArrayInputStream(bytes), meta);
            try (COSObject obj = cosClient.getObject(bucket, key)) {
                try (InputStream in = obj.getObjectContent()) {
                    byte[] read = in.readAllBytes();
                    if (read.length != bytes.length) {
                        throw new StorageException("存储前缀读写校验失败: 读取长度不一致");
                    }
                }
            }
        } catch (CosServiceException e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getErrorMessage(), e);
        } catch (CosClientException | IOException e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                cosClient.deleteObject(bucket, key);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * COS 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider cosStorageEngineProvider() {
        return new CosStorageEngineProvider();
    }

}
