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
package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.DirectDownloadPolicy;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.core.StorageEngine;
import com.richie.context.utils.data.JsonUtils;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;


/**
 * 亚马逊S3对象存储引擎
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-05 17:42:42
 */
@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aws_s3")
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
public final class S3StorageEngine extends AbstractObjectStorageEngine<S3Client> implements StorageEngine {

    @Autowired
    private S3Presigner s3Presigner;

    public S3StorageEngine(StorageProperties properties,
                           StorageTypeConverter converter) {
        super(properties, converter);
    }

    public void setS3Presigner(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        var client = getClient(S3Client.class);
        try {
            var putObjectRequestBuilder = PutObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(key);

            var storageClass = getStorageClass();
            if (StringUtils.isNotEmpty(storageClass)) {
                putObjectRequestBuilder.storageClass(StorageClass.fromValue(storageClass));
            }

            // 设置 ACL（访问控制列表）
            ObjectCannedACL acl = getAcl();
            if (acl != null) {
                try {
                    putObjectRequestBuilder.acl(acl);
                } catch (IllegalArgumentException e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }

            var putObjectRequest = putObjectRequestBuilder.build();
            var putObjectResponse = client.putObject(putObjectRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(file));

            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .versionId(putObjectResponse.versionId())
                    .requestId(putObjectResponse.eTag())
                    .hashValue(putObjectResponse.eTag()) // AWS SDK 2.x不再提供contentMd5
                    .uploadTime(OffsetDateTime.now())
                    .url(buildObjectUrl(key))
                    .build();
        } catch (AwsServiceException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .requestId(e.requestId())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        } catch (SdkException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        key = getRealPath(key);
        var client = getClient(S3Client.class);
        try {
            var putObjectRequestBuilder = PutObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(key);

            var storageClass = getStorageClass();
            if (StringUtils.isNotEmpty(storageClass)) {
                putObjectRequestBuilder.storageClass(StorageClass.fromValue(storageClass));
            }

            // 设置 ACL（访问控制列表）
            ObjectCannedACL acl = getAcl();
            if (acl != null) {
                try {
                    putObjectRequestBuilder.acl(acl);
                } catch (IllegalArgumentException e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }

            var putObjectRequest = putObjectRequestBuilder.build();
            // 读取 InputStream 到字节数组，因为 AWS SDK 2.x 需要明确的内容长度
            byte[] bytes = inputStream.readAllBytes();
            var putObjectResponse = client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));

            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .versionId(putObjectResponse.versionId())
                    .requestId(putObjectResponse.eTag())
                    .hashValue(putObjectResponse.eTag()) // AWS SDK 2.x不再提供contentMd5
                    .uploadTime(OffsetDateTime.now())
                    .url(buildObjectUrl(key))
                    .build();
        } catch (AwsServiceException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .requestId(e.requestId())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        } catch (SdkException | IOException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        }
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options) {
        return putObject(key, file);
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream, ImageOptions options) {
        return putObject(key, inputStream);
    }

    @Override
    public <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeReference) {
        key = getRealPath(key);
        var client = getClient(S3Client.class);
        try {
            var getObjectResponse = client.getObject(GetObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(key)
                    .build());

            var builder = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(getObjectResponse))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return new DownloadResponse<T>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(getObjectResponse.response().eTag())
                        .setVersionId(getObjectResponse.response().versionId())
                        .setKey(key)
                        .setData(JsonUtils.getInstance().deserialize(builder.toString(), typeReference));
            } catch (IOException e) {
                return new DownloadResponse<T>()
                        .setSuccess(false)
                        .setErrorMessage(e.getMessage())
                        .setBucketName(getBucketName())
                        .setKey(key);
            }
        } catch (AwsServiceException e) {
            return DownloadResponse.<T>builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .requestId(e.requestId())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        } catch (SdkException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        key = getRealPath(key);
        if (!targetPath.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setBucketName(getBucketName())
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
        var client = getClient(S3Client.class);
        try {
            var getObjectResponse = client.getObject(GetObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(key)
                    .build());

            try (var inputStream = getObjectResponse;
                 var outputStream = new FileOutputStream(targetPath)) {
                var buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
                if (returnData) {
                    // 通过 nio 读取 targetFile 文件内容并转换为字节流
                    return new DownloadResponse<byte[]>()
                            .setSuccess(true)
                            .setErrorMessage(null)
                            .setBucketName(getBucketName())
                            .setRequestId(getObjectResponse.response().eTag())
                            .setVersionId(getObjectResponse.response().versionId())
                            .setKey(key)
                            .setData(Files.readAllBytes(targetPath.toPath()));
                }
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setBucketName(getBucketName())
                        .setRequestId(getObjectResponse.response().eTag())
                        .setVersionId(getObjectResponse.response().versionId())
                        .setKey(key);
            } catch (IOException e) {
                return new DownloadResponse<byte[]>()
                        .setSuccess(false)
                        .setErrorMessage(e.getMessage())
                        .setBucketName(getBucketName())
                        .setKey(key);
            }
        } catch (AwsServiceException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(e.requestId())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (SdkException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        key = getRealPath(key);
        var targetFile = new File(targetPath);
        if (!targetFile.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setBucketName(getBucketName())
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
        var client = getClient(S3Client.class);

        try {
            // 使用标准的S3Client下载方法
            var getObjectResponse = client.getObject(GetObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(key)
                    .build());

            try (var inputStream = getObjectResponse;
                 var outputStream = new FileOutputStream(targetFile)) {
                var buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
            }

            // 下载完成后，获取下载内容并转为byte数组
            if (returnData) {
                // 通过 nio 读取 targetFile 文件内容并转换为字节流
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(getObjectResponse.response().eTag())
                        .setVersionId(getObjectResponse.response().versionId())
                        .setKey(key)
                        .setData(Files.readAllBytes(targetFile.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(getObjectResponse.response().eTag())
                    .setVersionId(getObjectResponse.response().versionId())
                    .setKey(key);
        } catch (AwsServiceException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(e.requestId())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (SdkException | IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        }
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        var client = getClient(S3Client.class);
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            log.error("检查对象存在性时发生异常: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(realKey)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .putObjectRequest(putObjectRequest)
                    .signatureDuration(Duration.ofSeconds(safeExpire))
                    .build();
            PresignedPutObjectRequest signed = s3Presigner.presignPutObject(presignRequest);
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(signed.url().toString())
                    .headers(signed.signedHeaders().entrySet().stream()
                            .filter(entry -> !entry.getValue().isEmpty())
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().getFirst())))
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("S3 预签名签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        }
    }

    @Override
    public DirectDownloadPolicy issueDirectDownloadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(getBucketName())
                    .key(realKey)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getObjectRequest)
                    .signatureDuration(Duration.ofSeconds(safeExpire))
                    .build();
            PresignedGetObjectRequest signed = s3Presigner.presignGetObject(presignRequest);
            return DirectDownloadPolicy.builder()
                    .success(true)
                    .downloadUrl(signed.url().toString())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("S3 下载预签名签发失败，降级兜底直读链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectDownloadPolicy(key, safeExpire);
        }
    }

    /**
     * 构建对象访问URL
     * <p>
     * AWS S3 标准URL格式：<a href="https://bucket-name.s3.region.amazonaws.com/key">...</a>
     * 如果 endpoint 包含协议前缀，则移除
     *
     * @param key 对象键
     * @return 对象访问URL
     */
    private String buildObjectUrl(String key) {
        String endpoint = objectConfig().getEndpoint();
        // 移除协议前缀（如果存在）
        if (endpoint.startsWith("http://")) {
            endpoint = endpoint.substring(7);
        } else if (endpoint.startsWith("https://")) {
            endpoint = endpoint.substring(8);
        }
        return "https://%s.%s/%s".formatted(getBucketName(), endpoint, key);
    }
}
