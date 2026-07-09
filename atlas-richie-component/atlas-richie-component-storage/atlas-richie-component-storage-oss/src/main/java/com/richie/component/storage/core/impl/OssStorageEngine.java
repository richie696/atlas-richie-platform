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
package com.richie.component.storage.core.impl;

import com.aliyun.oss.*;
import com.aliyun.oss.internal.OSSHeaders;
import com.aliyun.oss.model.*;
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
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.UUID;


/**
 * 阿里云OSS对象存储引擎
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-05 17:42:29
 */
@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aliyun_oss")
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
public final class OssStorageEngine extends AbstractObjectStorageEngine<OSS> implements StorageEngine {

    public OssStorageEngine(StorageProperties properties,
                            StorageTypeConverter converter) {
        super(properties, converter);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        var ossClient = getClient(OSS.class);
        var metadata = new ObjectMetadata();
        var storageClass = getStorageClass();
        metadata.setContentType("application/octet-stream");
        try {
            if (StringUtils.isNotBlank(storageClass)) {
                metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.parse(storageClass));
            }
            var putObjectRequest = new PutObjectRequest(getBucketName(), key, file, metadata);
            // 执行上传操作
            var result = ossClient.putObject(putObjectRequest);

            // 设置 ACL（访问控制列表）- 上传后单独设置 ACL
            CannedAccessControlList acl = getAcl();
            if (acl != null) {
                try {
                    ossClient.setObjectAcl(getBucketName(), key, acl);
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }
            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .versionId(result.getVersionId())
                    .requestId(result.getRequestId())
                    .hashValue(result.getETag())
                    .uploadTime(OffsetDateTime.now())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (OSSException oe) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            log.error("Error Message:" + oe.getErrorMessage());
            log.error("Error Code:" + oe.getErrorCode());
            log.error("Request ID:" + oe.getRequestId());
            log.error("Host ID:" + oe.getHostId());
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(oe.getErrorMessage())
                    .requestId(oe.getRequestId())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (ClientException ce) {
            log.error("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            log.error("Error Message:" + ce.getMessage());
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(ce.getErrorMessage())
                    .requestId(ce.getRequestId())
                    .build();
        } finally {
            destroy(ossClient);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        key = getRealPath(key);
        var ossClient = getClient(OSS.class);
        var metadata = new ObjectMetadata();
        var storageClass = getStorageClass();
        metadata.setContentType("application/octet-stream");
        try {
            if (StringUtils.isNotBlank(storageClass)) {
                metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.parse(storageClass));
            }
            var putObjectRequest = new PutObjectRequest(getBucketName(), key, inputStream, metadata);
            // 执行上传操作
            var result = ossClient.putObject(putObjectRequest);

            // 设置 ACL（访问控制列表）- 上传后单独设置 ACL
            CannedAccessControlList acl = getAcl();
            if (acl != null) {
                try {
                    ossClient.setObjectAcl(getBucketName(), key, acl);
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }
            return UploadResponse.builder()
                    .success(StringUtils.isNotBlank(result.getETag()))
                    .errorMessage(result.getResponse() != null ? result.getResponse().getErrorResponseAsString() : "")
                    .bucketName(getBucketName())
                    .versionId(result.getVersionId())
                    .requestId(result.getRequestId())
                    .uploadTime(OffsetDateTime.now())
                    .hashValue(result.getServerCRC().toString())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();

        } catch (OSSException oe) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            log.error("Error Message:" + oe.getErrorMessage());
            log.error("Error Code:" + oe.getErrorCode());
            log.error("Request ID:" + oe.getRequestId());
            log.error("Host ID:" + oe.getHostId());
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(oe.getErrorMessage())
                    .requestId(oe.getRequestId())
                    .build();
        } catch (ClientException ce) {
            log.error("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            log.error("Error Message:" + ce.getMessage());
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(ce.getErrorMessage())
                    .requestId(ce.getRequestId())
                    .build();
        } finally {
            destroy(ossClient);
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
        var ossClient = getClient(OSS.class);
        // ossObject包含文件所在的存储空间名称、文件名称、文件元信息以及一个输入流。
        var builder = new StringBuilder();
        String requestId = null;
        try (var ossObject = ossClient.getObject(getBucketName(), key);
             var reader = new BufferedReader(new InputStreamReader(ossObject.getObjectContent()))) {
            requestId = ossObject.getObjectMetadata().getRequestId();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new DownloadResponse<T>()
                    .setSuccess(true)
                    .setErrorMessage(null)
                    .setBucketName(getBucketName())
                    .setRequestId(requestId)
                    .setKey(key)
                    .setData(JsonUtils.getInstance().deserialize(builder.toString(), typeReference));
        } catch (OSSException oe) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(oe.getErrorMessage())
                    .setRequestId(oe.getRequestId());
        } catch (ClientException ce) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(ce.getMessage())
                    .setRequestId(ce.getRequestId());
        } catch (IOException ioe) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(ioe.getMessage())
                    .setRequestId(requestId);
        } finally {
            destroy(ossClient);
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
        var request = new GetObjectRequest(getBucketName(), key);

        var ossClient = getClient(OSS.class);
        try {
            var metadata = ossClient.getObject(request, targetPath);
            if (returnData) {
                // 通过 nio 读取 targetFile 文件内容并转换为字节流
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(metadata.getRequestId())
                        .setKey(key)
                        .setData(Files.readAllBytes(targetPath.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(metadata.getRequestId())
                    .setKey(key)
                    .setVersionId(metadata.getVersionId())
                    .setContentType(metadata.getContentType())
                    .setContentMD5(metadata.getContentMD5())
                    .setContentEncoding(metadata.getContentEncoding());
        } catch (OSSException oe) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setRequestId(oe.getRequestId())
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(oe.getErrorMessage());
        } catch (ClientException ce) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(ce.getErrorMessage())
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""));
        } catch (IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""));
        } finally {
            destroy(ossClient);
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
        var ossClient = getClient(OSS.class);
        try {
            // 请求10个任务并发下载
            var downloadFileRequest = new DownloadFileRequest(getBucketName(), key);
            // 指定Object下载到本地文件的完整路径
            downloadFileRequest.setDownloadFile(targetPath);
            // 设置分片大小，单位为字节，取值范围为100 KB~5 GB。默认值为100 KB。
            downloadFileRequest.setPartSize(1024 * 1024);
            // 设置分片下载的并发数，默认值为1。
            downloadFileRequest.setTaskNum(10);
            // 开启断点续传下载，默认关闭。
            downloadFileRequest.setEnableCheckpoint(true);
            // 设置断点记录文件的完整路径，例如D:\\localpath\\examplefile.txt.dcp。
            // 只有当Object下载中断产生了断点记录文件后，如果需要继续下载该Object，才需要设置对应的断点记录文件。下载完成后，该文件会被删除。
            downloadFileRequest.setCheckpointFile(targetPath + ".dcp");
            // 下载文件。
            var downloadRes = ossClient.downloadFile(downloadFileRequest);
            // 下载成功时，会返回文件元信息。
            var metadata = downloadRes.getObjectMetadata();
            if (returnData) {
                // 通过 nio 读取 targetFile 文件内容并转换为字节流
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(metadata.getRequestId())
                        .setKey(key)
                        .setData(Files.readAllBytes(targetFile.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(metadata.getRequestId())
                    .setKey(key)
                    .setVersionId(metadata.getVersionId())
                    .setContentType(metadata.getContentType())
                    .setContentMD5(metadata.getContentMD5())
                    .setContentEncoding(metadata.getContentEncoding());
        } catch (InconsistentException ce) {
            log.error("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            log.error("Error Message:" + ce.getMessage());
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setRequestId(ce.getRequestId())
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(ce.getMessage());
        } catch (Throwable e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setErrorMessage(e.getMessage());
        } finally {
            destroy(ossClient);
        }
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        var ossClient = getClient(OSS.class);
        try {
            return ossClient.doesObjectExist(getBucketName(), key);
        } finally {
            destroy(ossClient);
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var ossClient = getClient(OSS.class);
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(safeExpire));
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(getBucketName(), realKey, HttpMethod.PUT);
            request.setExpiration(expiration);
            var url = ossClient.generatePresignedUrl(request);
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(url.toString())
                    .headers(Map.of())
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("OSS 预签名签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        } finally {
            destroy(ossClient);
        }
    }

    @Override
    public DirectDownloadPolicy issueDirectDownloadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var ossClient = getClient(OSS.class);
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(safeExpire));
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(getBucketName(), realKey, HttpMethod.GET);
            request.setExpiration(expiration);
            var url = ossClient.generatePresignedUrl(request);
            return DirectDownloadPolicy.builder()
                    .success(true)
                    .downloadUrl(url.toString())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("OSS 下载预签名签发失败，降级兜底直读链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectDownloadPolicy(key, safeExpire);
        } finally {
            destroy(ossClient);
        }
    }

    @Override
    public void destroy(@Nonnull OSS ossClient) {
        ossClient.shutdown();
    }
}
