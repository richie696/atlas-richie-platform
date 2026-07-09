/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;

/**
 * 文件存储自动配置类
 *
 * @author richie696
 * @version 1.1
 * @since 2023-09-05 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class S3AutoConfiguration {

    /**
     * AWS S3客户端
     *
     * @param properties 存储配置
     * @return 返回AWS S3客户端
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aws_s3")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public S3Client s3Client(StorageProperties properties) throws StorageException {
        ObjectConfig config = properties.getObject();

        // 构建 endpoint URI，确保包含协议前缀
        String endpoint = config.getEndpoint();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        java.net.URI endpointUri = java.net.URI.create(endpoint);

        // 构建S3客户端
        S3Client s3Client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getAccessKeySecret())
                ))
                .build();

        if (!config.isAutoCreateBucket()) {
            verifyS3Prefix(s3Client, config);
            return s3Client;
        }

        try {
            // 检查桶是否存在
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(config.getBucketName())
                    .build());
            log.info("S3桶 {} 已存在", config.getBucketName());
        } catch (NoSuchBucketException e) {
            // 桶不存在，创建桶
            log.info("S3桶 {} 不存在，正在创建", config.getBucketName());
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(config.getBucketName())
                    .build());
            log.info("S3桶 {} 创建成功", config.getBucketName());
        } catch (AwsServiceException e) {
            log.error("AWS服务异常: {}", e.getMessage());
            throw new StorageException("AWS服务异常: %s".formatted(e.getMessage()));
        } catch (SdkException e) {
            log.error("SDK异常: {}", e.getMessage());
            throw new StorageException("SDK异常: %s".formatted(e.getMessage()));
        }

        return s3Client;
    }

    private void verifyS3Prefix(S3Client s3Client, ObjectConfig config) throws StorageException {
        String bucket = config.getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromBytes(bytes));
            try (var resp = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
                resp.readAllBytes();
            }
        } catch (SdkException | IOException e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * AWS S3预签名客户端，复用与S3Client相同的配置
     * <p>
     * S3Presigner 线程安全，创建成本高（含HTTP连接池和签名器），
     * 注册为Spring Bean后由容器管理生命周期，应用关闭时自动销毁。
     *
     * @param properties 存储配置
     * @return 返回AWS S3预签名客户端
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aws_s3")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public S3Presigner s3Presigner(StorageProperties properties) {
        ObjectConfig config = properties.getObject();

        String endpoint = config.getEndpoint();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        java.net.URI endpointUri = java.net.URI.create(endpoint);

        return S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getAccessKeySecret())
                ))
                .build();
    }

    /**
     * S3 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider s3StorageEngineProvider() {
        return new S3StorageEngineProvider();
    }

}
