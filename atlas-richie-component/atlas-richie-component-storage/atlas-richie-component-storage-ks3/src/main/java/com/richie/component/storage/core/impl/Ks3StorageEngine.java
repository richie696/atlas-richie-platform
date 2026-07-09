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

import com.ksyun.ks3.dto.CannedAccessControlList;
import com.ksyun.ks3.dto.GetObjectResult;
import com.ksyun.ks3.dto.ObjectMetadata;
import com.ksyun.ks3.exception.Ks3ClientException;
import com.ksyun.ks3.exception.Ks3ServiceException;
import com.ksyun.ks3.http.HttpHeaders;
import com.ksyun.ks3.service.Ks3;
import com.ksyun.ks3.service.request.GetObjectRequest;
import com.ksyun.ks3.service.request.PutObjectACLRequest;
import com.ksyun.ks3.service.request.PutObjectRequest;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;


/**
 * 金山云KS3对象存储引擎
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-05 17:42:10
 */
@Slf4j
@Component("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "ksyun_ks3")
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
public final class Ks3StorageEngine extends AbstractObjectStorageEngine<Ks3> implements StorageEngine {

    public Ks3StorageEngine(StorageProperties properties,
                            StorageTypeConverter converter) {
        super(properties, converter);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        var client = getClient(Ks3.class);
        try {
            var storageClass = getStorageClass();
            PutObjectRequest request;
            if (StringUtils.isNotBlank(storageClass)) {
                var metadata = new ObjectMetadata();
                metadata.setHeader(HttpHeaders.StorageClass.toString(), storageClass);
                request = new PutObjectRequest(getBucketName(), key, file, metadata);
            } else {
                request = new PutObjectRequest(getBucketName(), key, file);
            }
            var result = client.putObject(request);

            // 设置 ACL（访问控制列表）- 上传后单独设置 ACL
            CannedAccessControlList acl = getAcl();
            if (acl != null) {
                try {
                    var putObjectAclRequest = new PutObjectACLRequest(getBucketName(), key);
                    putObjectAclRequest.setCannedAcl(acl);
                    client.putObjectACL(putObjectAclRequest);
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }
            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .requestId(result.getRequestId())
                    .hashValue(result.getCrc64Ecma())
                    .uploadTime(OffsetDateTime.now())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (Ks3ServiceException e) {
            log.error("Http Status: " + e.getStatusCode());
            log.error("Error Code: " + e.getStatusCode());
            log.error("Error Message: " + e.getErrorMessage());
            log.error("Request ID: " + e.getRequestId());
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getErrorMessage())
                    .requestId(e.getRequestId())
                    .build();
        } finally {
            destroy(client);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        key = getRealPath(key);
        var client = getClient(Ks3.class);
        try {
            var storageClass = getStorageClass();
            PutObjectRequest request;
            if (StringUtils.isNotBlank(storageClass)) {
                var metadata = new ObjectMetadata();
                metadata.setHeader(HttpHeaders.StorageClass.toString(), storageClass);
                request = new PutObjectRequest(getBucketName(), key, inputStream, metadata);
            } else {
                request = new PutObjectRequest(getBucketName(), key, inputStream);
            }
            var result = client.putObject(request);

            // 设置 ACL（访问控制列表）- 上传后单独设置 ACL
            CannedAccessControlList acl = getAcl();
            if (acl != null) {
                try {
                    var putObjectAclRequest = new PutObjectACLRequest(getBucketName(), key);
                    putObjectAclRequest.setCannedAcl(acl);
                    client.putObjectACL(putObjectAclRequest);
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }
            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .requestId(result.getRequestId())
                    .hashValue(result.getCrc64Ecma())
                    .uploadTime(OffsetDateTime.now())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (Ks3ServiceException e) {
            log.error("Http Status: " + e.getStatusCode());
            log.error("Error Code: " + e.getStatusCode());
            log.error("Error Message: " + e.getErrorMessage());
            log.error("Request ID: " + e.getRequestId());
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getErrorMessage())
                    .requestId(e.getRequestId())
                    .build();
        } finally {
            destroy(client);
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
        var client = getClient(Ks3.class);
        var builder = new StringBuilder();
        String requestId;
        GetObjectResult result;
        var request = new GetObjectRequest(getBucketName(), key);
        try {
            result = client.getObject(request);
            requestId = result.getRequestId();
        } catch (Ks3ClientException ce) {
            destroy(client);
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(ce.getMessage());
        }
        try (var ks3Object = result.getObject();
             var reader = new BufferedReader(new InputStreamReader(ks3Object.getObjectContent()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new DownloadResponse<T>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(requestId)
                    .setKey(key)
                    .setContentType(result.getObject().getObjectMetadata().getContentType())
                    .setContentMD5(result.getObject().getObjectMetadata().getContentMD5())
                    .setContentEncoding(result.getObject().getObjectMetadata().getContentEncoding())
                    .setData(JsonUtils.getInstance().deserialize(builder.toString(), typeReference));
        } catch (IOException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(e.getMessage());
        } finally {
            destroy(client);
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
        var client = getClient(Ks3.class);
        String requestId;
        GetObjectResult result;
        var request = new GetObjectRequest(getBucketName(), key);
        try {
            result = client.getObject(request);
            requestId = result.getRequestId();
        } catch (Ks3ClientException ce) {
            destroy(client);
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(ce.getMessage());
        }
        try (var ks3Object = result.getObject();
             var inputStream = new BufferedInputStream(ks3Object.getObjectContent());
             var outputStream = new BufferedOutputStream(new FileOutputStream(targetPath))) {
            var buffer = new byte[1024 * 1024];
            int bytesRead;
            // 循环获取输入流，并写入文件
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            if (returnData) {
                // 通过 nio 读取 targetFile 文件内容并转换为字节流
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(requestId)
                        .setKey(key)
                        .setData(Files.readAllBytes(targetPath.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(requestId)
                    .setKey(key)
                    .setContentType(result.getObject().getObjectMetadata().getContentType())
                    .setContentMD5(result.getObject().getObjectMetadata().getContentMD5())
                    .setContentEncoding(result.getObject().getObjectMetadata().getContentEncoding());
        } catch (IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setRequestId(requestId)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(e.getMessage());
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
        return getObject(key, targetFile, returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        var client = getClient(Ks3.class);
        try {
            return client.objectExists(getBucketName(), key);
        } finally {
            destroy(client);
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var client = getClient(Ks3.class);
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(safeExpire));
            Object signedUrl = invokeKs3Presign(client, getBucketName(), realKey, expiration);
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(String.valueOf(signedUrl))
                    .headers(Map.of())
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("KS3 预签名签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        } finally {
            destroy(client);
        }
    }

    @Override
    public DirectDownloadPolicy issueDirectDownloadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var client = getClient(Ks3.class);
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(safeExpire));
            Object signedUrl = invokeKs3PresignForGet(client, getBucketName(), realKey, expiration);
            return DirectDownloadPolicy.builder()
                    .success(true)
                    .downloadUrl(String.valueOf(signedUrl))
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("KS3 下载预签名签发失败，降级兜底直读链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectDownloadPolicy(key, safeExpire);
        } finally {
            destroy(client);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object invokeKs3PresignForGet(Ks3 client, String bucket, String key, Date expiration) throws Exception {
        Class<?> clazz = client.getClass();
        for (var method : clazz.getMethods()) {
            if (!"generatePresignedUrl".equals(method.getName())) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 3
                    && paramTypes[0] == String.class
                    && paramTypes[1] == String.class
                    && Date.class.isAssignableFrom(paramTypes[2])) {
                return method.invoke(client, bucket, key, expiration);
            }
            if (paramTypes.length == 4
                    && paramTypes[0] == String.class
                    && paramTypes[1] == String.class
                    && Date.class.isAssignableFrom(paramTypes[2])
                    && paramTypes[3].isEnum()) {
                Enum get = Enum.valueOf((Class<? extends Enum>) paramTypes[3], "GET");
                return method.invoke(client, bucket, key, expiration, get);
            }
        }
        throw new NoSuchMethodException("Ks3 generatePresignedUrl method not found");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object invokeKs3Presign(Ks3 client, String bucket, String key, Date expiration) throws Exception {
        Class<?> clazz = client.getClass();
        for (var method : clazz.getMethods()) {
            if (!"generatePresignedUrl".equals(method.getName())) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 3
                    && paramTypes[0] == String.class
                    && paramTypes[1] == String.class
                    && Date.class.isAssignableFrom(paramTypes[2])) {
                return method.invoke(client, bucket, key, expiration);
            }
            if (paramTypes.length == 4
                    && paramTypes[0] == String.class
                    && paramTypes[1] == String.class
                    && Date.class.isAssignableFrom(paramTypes[2])
                    && paramTypes[3].isEnum()) {
                Enum put = Enum.valueOf((Class<? extends Enum>) paramTypes[3], "PUT");
                return method.invoke(client, bucket, key, expiration, put);
            }
        }
        throw new NoSuchMethodException("Ks3 generatePresignedUrl method not found");
    }

    @Override
    public void destroy(@Nonnull Ks3 ks3Client) {
        ks3Client.shutdown();
    }
}
