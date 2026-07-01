package com.richie.component.storage.core.impl;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.*;
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
 * 华为云OBS对象存储引擎
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-05 17:41:35
 */
@Slf4j
@Service("objectStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "huawei_obs")
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
public final class ObsStorageEngine extends AbstractObjectStorageEngine<ObsClient> implements StorageEngine {

    public ObsStorageEngine(StorageProperties properties,
                            StorageTypeConverter converter) {
        super(properties, converter);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        var client = getClient(ObsClient.class);
        key = getRealPath(key);
        try {
            var storageClass = getStorageClass();
            var putObjectRequest = new PutObjectRequest(getBucketName(), key, file);
            if (StringUtils.isNotBlank(storageClass)) {
                var metadata = new ObjectMetadata();
                metadata.setObjectStorageClass(StorageClassEnum.getValueFromCode(storageClass));
                // 设置存储类型
                putObjectRequest.setMetadata(metadata);
            }

            // 执行上传操作
            var putObjectResult = client.putObject(putObjectRequest);

            // 设置 ACL（访问控制列表）- 上传后单独设置 ACL
            AccessControlList acl = getAcl();
            if (acl != null) {
                try {
                    client.setObjectAcl(getBucketName(), key, acl);
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }

            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .versionId(putObjectResult.getVersionId())
                    .requestId(putObjectResult.getRequestId())
                    .uploadTime(OffsetDateTime.now())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (ObsException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getErrorMessage())
                    .requestId(e.getErrorRequestId())
                    .bucketName(getBucketName())
                    .key(objectConfig().getBasePath() + key)
                    .build();
        } finally {
            destroy(client);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        var client = getClient(ObsClient.class);
        key = getRealPath(key);
        try {
            var storageClass = getStorageClass();
            var putObjectRequest = new PutObjectRequest(getBucketName(), key, inputStream);
            if (StringUtils.isNotBlank(storageClass)) {
                // metadata参数参考：https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0606.html
                var metadata = new ObjectMetadata();
                metadata.setObjectStorageClass(StorageClassEnum.getValueFromCode(storageClass));
                // 设置存储类型
                putObjectRequest.setMetadata(metadata);
            }

            // 执行上传操作
            var putObjectResult = client.putObject(putObjectRequest);

            // 设置 ACL（访问控制列表）- 上传后单独设置 ACL
            AccessControlList acl = getAcl();
            if (acl != null) {
                try {
                    client.setObjectAcl(getBucketName(), key, acl);
                } catch (Exception e) {
                    log.warn("设置 ACL 失败: {}, 将使用默认 ACL (PRIVATE). 错误: {}", acl, e.getMessage());
                }
            }

            return UploadResponse.builder()
                    .success(true)
                    .bucketName(getBucketName())
                    .versionId(putObjectResult.getVersionId())
                    .requestId(putObjectResult.getRequestId())
                    .uploadTime(OffsetDateTime.now())
                    .url("https://" + getBucketName() + "." + objectConfig().getEndpoint() + "/" + key)
                    .build();
        } catch (ObsException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getErrorMessage())
                    .requestId(e.getErrorRequestId())
                    .bucketName(getBucketName())
                    .key(key)
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
        var client = getClient(ObsClient.class);
        key = getRealPath(key);
        var obsObject = client.getObject(getBucketName(), key);
        var builder = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(obsObject.getObjectContent()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new DownloadResponse<T>()
                    .setSuccess(true)
                    .setErrorMessage(null)
                    .setBucketName(getBucketName())
                    .setRequestId(obsObject.getMetadata().getRequestId())
                    .setKey(key)
                    .setData(JsonUtils.getInstance().deserialize(builder.toString(), typeReference));
        } catch (IOException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
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
        var client = getClient(ObsClient.class);
        ObsObject obsObject;
        try {
            obsObject = client.getObject(getBucketName(), key);
        } catch (ObsException e) {
            return DownloadResponse.<byte[]>builder()
                    .success(false)
                    .errorMessage(e.getErrorMessage())
                    .requestId(e.getErrorRequestId())
                    .bucketName(getBucketName())
                    .key(key)
                    .build();
        }
        try (var inputStream = obsObject.getObjectContent();
             var outputStream = new FileOutputStream(targetPath)) {
            var buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            if (returnData) {
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(obsObject.getMetadata().getRequestId())
                        .setKey(key)
                        .setData(Files.readAllBytes(targetPath.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setErrorMessage(null)
                    .setBucketName(getBucketName())
                    .setRequestId(obsObject.getMetadata().getRequestId())
                    .setKey(key);
        } catch (IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } finally {
            destroy(client);
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
        var client = getClient(ObsClient.class);
        var request = new DownloadFileRequest(getBucketName(), key);
        request.setDownloadFile(targetPath);
        request.setTaskNum(10);
        request.setPartSize(10 * 1024 * 1024);
        request.setEnableCheckpoint(true);
        try {
            // 进行断点续传下载
            var result = client.downloadFile(request);
            if (returnData) {
                // 通过 nio 读取 targetFile 文件内容并转换为字节流
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setErrorMessage(null)
                        .setBucketName(getBucketName())
                        .setRequestId(result.getObjectMetadata().getEtag())
                        .setKey(key)
                        .setData(Files.readAllBytes(targetFile.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setBucketName(getBucketName())
                    .setRequestId(result.getObjectMetadata().getEtag())
                    .setKey(key);
        } catch (ObsException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getErrorMessage())
                    .setRequestId(e.getErrorRequestId())
                    .setBucketName(getBucketName())
                    .setKey(key);
        } catch (IOException e) {
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
        var client = getClient(ObsClient.class);
        try {
            return client.doesObjectExist(getBucketName(), key);
        } finally {
            destroy(client);
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var client = getClient(ObsClient.class);
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(safeExpire));
            Object request = buildObsTemporarySignatureRequest(realKey, expiration, safeExpire);
            Object response = client.getClass().getMethod("createTemporarySignature", request.getClass()).invoke(client, request);
            String signedUrl = String.valueOf(response.getClass().getMethod("getSignedUrl").invoke(response));
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) response.getClass()
                    .getMethod("getActualSignedRequestHeaders")
                    .invoke(response);
            return DirectUploadPolicy.builder()
                    .success(true)
                    .method("PUT")
                    .uploadUrl(signedUrl)
                    .headers(headers == null ? Map.of() : headers)
                    .formFields(Map.of())
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("OBS 预签名签发失败，降级兜底直传链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectUploadPolicy(key, safeExpire);
        } finally {
            destroy(client);
        }
    }

    @Override
    public DirectDownloadPolicy issueDirectDownloadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String realKey = getRealPath(key);
        var client = getClient(ObsClient.class);
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(safeExpire));
            Object request = buildObsTemporarySignatureRequestForGet(realKey, expiration, safeExpire);
            Object response = client.getClass().getMethod("createTemporarySignature", request.getClass()).invoke(client, request);
            String signedUrl = String.valueOf(response.getClass().getMethod("getSignedUrl").invoke(response));
            return DirectDownloadPolicy.builder()
                    .success(true)
                    .downloadUrl(signedUrl)
                    .bucketName(getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("OBS 下载预签名签发失败，降级兜底直读链接。key={}, error={}", realKey, e.getMessage());
            return buildFallbackDirectDownloadPolicy(key, safeExpire);
        } finally {
            destroy(client);
        }
    }

    /**
     * 重载版本：指定 HTTP 方法（GET/PUT）构建 OBS 临时签名请求。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object buildObsTemporarySignatureRequestForGet(String realKey, Date expiration, int safeExpire) throws Exception {
        Class<?> requestClass = Class.forName("com.obs.services.model.TemporarySignatureRequest");
        Class<? extends Enum> methodEnumClass =
                (Class<? extends Enum>) Class.forName("com.obs.services.model.HttpMethodEnum");
        Enum get = Enum.valueOf(methodEnumClass, "GET");

        Object request;
        try {
            request = requestClass.getConstructor(methodEnumClass, int.class).newInstance(get, safeExpire);
        } catch (NoSuchMethodException ignore) {
            try {
                request = requestClass.getConstructor(methodEnumClass, long.class).newInstance(get, (long) safeExpire);
            } catch (NoSuchMethodException ex) {
                request = requestClass.getConstructor().newInstance();
                requestClass.getMethod("setHttpMethod", methodEnumClass).invoke(request, get);
                requestClass.getMethod("setExpires", Date.class).invoke(request, expiration);
            }
        }
        requestClass.getMethod("setBucketName", String.class).invoke(request, getBucketName());
        requestClass.getMethod("setObjectKey", String.class).invoke(request, realKey);
        return request;
    }

    

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object buildObsTemporarySignatureRequest(String realKey, Date expiration, int safeExpire) throws Exception {
        Class<?> requestClass = Class.forName("com.obs.services.model.TemporarySignatureRequest");
        Class<? extends Enum> methodEnumClass =
                (Class<? extends Enum>) Class.forName("com.obs.services.model.HttpMethodEnum");
        Enum put = Enum.valueOf(methodEnumClass, "PUT");

        Object request;
        try {
            request = requestClass.getConstructor(methodEnumClass, int.class).newInstance(put, safeExpire);
        } catch (NoSuchMethodException ignore) {
            try {
                request = requestClass.getConstructor(methodEnumClass, long.class).newInstance(put, (long) safeExpire);
            } catch (NoSuchMethodException ex) {
                request = requestClass.getConstructor().newInstance();
                requestClass.getMethod("setHttpMethod", methodEnumClass).invoke(request, put);
                requestClass.getMethod("setExpires", Date.class).invoke(request, expiration);
            }
        }
        requestClass.getMethod("setBucketName", String.class).invoke(request, getBucketName());
        requestClass.getMethod("setObjectKey", String.class).invoke(request, realKey);
        return request;
    }

    @Override
    public void destroy(@Nonnull ObsClient obsClient) {
        try {
            obsClient.close();
        } catch (IOException _) {
        }
    }
}
