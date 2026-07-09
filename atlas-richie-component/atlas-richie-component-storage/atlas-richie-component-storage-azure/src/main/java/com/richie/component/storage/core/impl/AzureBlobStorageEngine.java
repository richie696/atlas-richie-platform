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
package com.richie.component.storage.core.impl;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.richie.component.storage.bean.DirectDownloadPolicy;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.context.utils.data.JsonUtils;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;


/**
 * 微软Azure Blob存储引擎
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-05 17:42:42
 */
@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "azure_blob")
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
public final class AzureBlobStorageEngine extends AbstractObjectStorageEngine<BlobContainerClient> implements StorageEngine {

    /**
     * 构造函数
     * @param properties 存储配置
     */
    public AzureBlobStorageEngine(StorageProperties properties) {
        super(properties, null);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        try {
            return putObject(key, new FileInputStream(file));
        } catch (FileNotFoundException e) {
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
        // 获取客户端
        var blobContainerClient = getClient(BlobContainerClient.class);
        var blobClient = blobContainerClient.getBlobClient(key);
        // file 转 输入流
        try (inputStream) {
            // 上传文件
            blobClient.upload(inputStream, inputStream.available());
            // 获取文件上传结果
            var blobProperties = blobClient.getProperties();
            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .versionId(blobProperties.getVersionId())
                    .requestId(blobProperties.getRequestId())
                    .hashValue(new String(blobProperties.getContentMd5()))
                    .uploadTime(OffsetDateTime.now())
                    .url(objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (IOException e) {
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
        var blobContainerClient = getClient(BlobContainerClient.class);
        var blobClient = blobContainerClient.getBlobClient(key);
        // 根据key下载文件
        byte[] bytes = blobClient.downloadContent().toBytes();
        T obj = JsonUtils.getInstance().deserializePayload(bytes, typeReference);
        return DownloadResponse.<T>builder()
                .success(true)
                .bucketName(getBucketName())
                .key(key)
                .data(obj)
                .build();
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
        var blobContainerClient = getClient(BlobContainerClient.class);
        var blobClient = blobContainerClient.getBlobClient(key);
        byte[] bytes = blobClient.downloadContent().toBytes();
        return DownloadResponse.<byte[]>builder()
                .success(true)
                .bucketName(getBucketName())
                .key(key)
                .data(bytes)
                .build();
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        return getObject(key, new File(targetPath), returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        var blobContainerClient = getClient(BlobContainerClient.class);
        var blobClient = blobContainerClient.getBlobClient(key);
        return blobClient.exists();
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var blobContainerClient = getClient(BlobContainerClient.class);
        try {
            var blobClient = blobContainerClient.getBlobClient(realKey);
            BlobSasPermission permission = new BlobSasPermission()
                    .setCreatePermission(true)
                    .setWritePermission(true);
            BlobServiceSasSignatureValues values =
                    new BlobServiceSasSignatureValues(OffsetDateTime.now().plusSeconds(safeExpire), permission);
            String sas = blobClient.generateSas(values);
            String uploadUrl = blobClient.getBlobUrl() + "?" + sas;
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(uploadUrl)
                    .headers(Map.of("x-ms-blob-type", "BlockBlob"))
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("Azure Blob SAS 签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        }
    }

    @Override
    public DirectDownloadPolicy issueDirectDownloadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var blobContainerClient = getClient(BlobContainerClient.class);
        try {
            var blobClient = blobContainerClient.getBlobClient(realKey);
            var expiryTime = OffsetDateTime.now().plusSeconds(safeExpire);
            var permissions = new BlobSasPermission().setReadPermission(true);
            var sasValues = new BlobServiceSasSignatureValues(expiryTime, permissions);
            String sasToken = blobClient.generateSas(sasValues);
            String downloadUrl = blobClient.getBlobUrl() + "?" + sasToken;
            return DirectDownloadPolicy.builder()
                    .success(true)
                    .downloadUrl(downloadUrl)
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(expiryTime)
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("Azure 下载预签名签发失败，降级兜底直读链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectDownloadPolicy(key, safeExpire);
        }
    }

}
