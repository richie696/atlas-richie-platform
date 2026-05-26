package com.richie.component.storage.core.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import cn.hutool.core.lang.UUID;
import tools.jackson.core.type.TypeReference;
import io.minio.*;
import io.minio.http.Method;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.XmlParserException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "minio")
public final class MinioStorageEngine extends AbstractObjectStorageEngine<MinioAsyncClient> implements StorageEngine {

    public MinioStorageEngine(StorageProperties properties) {
        super(properties, null);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        var client = getClient(MinioAsyncClient.class);
        try (var bis = new BufferedInputStream(new FileInputStream(file))) {
            var argsBuilder = PutObjectArgs.builder()
                    .bucket(getBucketName())
                    .object(key)
                    .stream(bis, -1, 10485760);
            // 设置 ACL（访问控制列表）
            String acl = getAcl();
            if (StringUtils.isNotBlank(acl)) {
                try {
                    // MinIO 使用 S3 兼容的 ACL 值
                    argsBuilder.headers(Map.of("x-amz-acl", acl));
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }
            var args = argsBuilder.build();
            var future = client.putObject(args);
            var response = future.get();
            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .requestId(response.etag())
                    .versionId(response.versionId())
                    .uploadTime(OffsetDateTime.now())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (IOException | InsufficientDataException | NoSuchAlgorithmException | InvalidKeyException |
                 XmlParserException | InternalException | ExecutionException | InterruptedException e) {
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
        var client = getClient(MinioAsyncClient.class);
        try {
            var argsBuilder = PutObjectArgs.builder()
                    .bucket(getBucketName())
                    .object(key)
                    .stream(inputStream, -1, 10485760);
            // 设置 ACL（访问控制列表）
            String acl = getAcl();
            if (StringUtils.isNotBlank(acl)) {
                try {
                    // MinIO 使用 S3 兼容的 ACL 值
                    argsBuilder.headers(Map.of("x-amz-acl", acl));
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }
            var args = argsBuilder.build();
            var future = client.putObject(args);
            var response = future.get();
            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .requestId(response.etag())
                    .versionId(response.versionId())
                    .uploadTime(OffsetDateTime.now())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (IOException | InsufficientDataException | NoSuchAlgorithmException | InvalidKeyException |
                 XmlParserException | InternalException | ExecutionException | InterruptedException e) {
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
        var client = getClient(MinioAsyncClient.class);
        var builder = new StringBuilder();
        var args = GetObjectArgs.builder()
                .bucket(getBucketName())
                .object(key)
                .build();
        GetObjectResponse response;
        try {
            var future = client.getObject(args);
            response = future.get();
        } catch (Exception e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        }
        try (var reader = new BufferedReader(new InputStreamReader(response))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new DownloadResponse<T>()
                    .setSuccess(true)
                    .setErrorMessage(null)
                    .setBucketName(getBucketName())
                    .setKey(key)
                    .setData(JsonUtils.getInstance().deserialize(builder.toString(), typeReference));
        } catch (Exception e) {
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
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
        var client = getClient(MinioAsyncClient.class);
        var args = GetObjectArgs.builder()
                .bucket(getBucketName())
                .object(key)
                .build();
        GetObjectResponse response;
        try {
            var future = client.getObject(args);
            response = future.get();
        } catch (Exception e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        }
        try (var outputStream = new FileOutputStream(targetPath)) {
            var buffer = new byte[1024];
            int len;
            while ((len = response.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            if (returnData) {
                // 通过 nio 读取 targetFile 文件内容并转换为字节流
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setKey(key)
                        .setData(Files.readAllBytes(targetPath.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (Exception e) {
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
        return getObject(key, targetFile, returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        var client = getClient(MinioAsyncClient.class);
        try {
            var args = StatObjectArgs.builder()
                    .bucket(getBucketName())
                    .object(key)
                    .build();
            var future = client.statObject(args);
            return future.get() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var client = getClient(MinioAsyncClient.class);
        try {
            String uploadUrl = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(getBucketName())
                    .object(realKey)
                    .expiry(safeExpire)
                    .build());
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(uploadUrl)
                    .headers(Map.of())
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("MinIO 预签名签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        }
    }
}
