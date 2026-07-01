package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.DirectDownloadPolicy;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import com.richie.context.utils.data.JsonUtils;
import io.minio.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "minio")
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
public final class MinioStorageEngine extends AbstractObjectStorageEngine<MinioAsyncClient> implements StorageEngine {

    /**
     * MinIO 异步客户端实例，由 Spring 容器注入（{@code prototype} 作用域）。
     * <p>
     * 在构造期注入避免业务方法中再走 {@code SpringContextHolder} 静态查找，
     * 同时保证单元测试中可直接 {@code new} 传入 mock 客户端。
     */
    private final MinioAsyncClient minioClient;

    public MinioStorageEngine(StorageProperties properties, MinioAsyncClient minioClient) {
        super(properties, null);
        this.minioClient = minioClient;
    }

    /**
     * 启动阶段对桶 / 前缀做一次探活；探活失败时仅记录告警而不阻塞 Spring 上下文启动，
     * 真实部署可在恢复后由运维触发重试或由首次业务调用按需补齐。
     */
    @PostConstruct
    public void initializeBucket() {
        if (objectConfig().isAutoCreateBucket()) {
            ensureBucketExists();
        } else {
            verifyMinioPrefix();
        }
    }

    /**
     * 检测目标桶是否存在，不存在则创建。所有异常降级为日志告警。
     */
    private void ensureBucketExists() {
        String bucket = getBucketName();
        try {
            BucketExistsArgs existsArgs = BucketExistsArgs.builder().bucket(bucket).build();
            boolean exists = minioClient.bucketExists(existsArgs).get(10, TimeUnit.SECONDS);
            if (!exists) {
                MakeBucketArgs makeArgs = MakeBucketArgs.builder().bucket(bucket).build();
                minioClient.makeBucket(makeArgs);
                log.info("MinIO 桶自动创建成功. bucket={}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO 桶自动创建失败，启动后首次业务调用将按需重试. bucket={}, endpoint={}, error={}",
                    bucket, objectConfig().getEndpoint(), e.getMessage());
        }
    }

    /**
     * 对 {@code basePath} 前缀做一次临时对象的写入 / 读取 / 删除校验，
     * 失败时降级为日志告警而非抛出 {@link StorageException}，避免阻塞 Spring 启动。
     */
    private void verifyMinioPrefix() {
        String bucket = getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(objectConfig().getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        try {
            minioClient.putObject(
                            PutObjectArgs.builder().bucket(bucket).object(key)
                                    .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L)
                                    .build())
                    .get(30, TimeUnit.SECONDS);
            try (var is = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())
                    .get(30, TimeUnit.SECONDS)) {
                is.readAllBytes();
            }
            log.info("MinIO 存储前缀读写校验成功. bucket={}, prefix={}", bucket, objectConfig().getBasePath());
        } catch (Exception e) {
            log.warn("MinIO 存储前缀读写校验失败，启动后首次业务调用将按需重试. bucket={}, prefix={}, error={}",
                    bucket, objectConfig().getBasePath(), e.getMessage());
        } finally {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build())
                        .get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 返回构造期注入的 MinIO 客户端，避免再走 {@code SpringContextHolder} 静态查找。
     *
     * @param clientClass 客户端类（必须为 {@link MinioAsyncClient}）
     * @return MinIO 异步客户端实例
     */
    @Override
    protected MinioAsyncClient getClient(Class<MinioAsyncClient> clientClass) {
        return minioClient;
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        var client = getClient(MinioAsyncClient.class);
        try (var bis = new BufferedInputStream(new FileInputStream(file))) {
            var argsBuilder = PutObjectArgs.builder()
                    .bucket(getBucketName())
                    .object(key)
                    .stream(bis, -1L, 10485760L);
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
        } catch (Exception e) {
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
                    .stream(inputStream, -1L, 10485760L);
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
        } catch (Exception e) {
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
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
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
                    .method(Http.Method.PUT)
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

    @Override
    public DirectDownloadPolicy issueDirectDownloadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var client = getClient(MinioAsyncClient.class);
        try {
            String downloadUrl = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(getBucketName())
                    .object(realKey)
                    .expiry(safeExpire)
                    .build());
            return DirectDownloadPolicy.builder()
                    .success(true)
                    .downloadUrl(downloadUrl)
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("MinIO 下载预签名签发失败，降级兜底直读链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectDownloadPolicy(key, safeExpire);
        }
    }
}
