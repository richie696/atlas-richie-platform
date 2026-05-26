package com.richie.component.storage.core.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import cn.hutool.core.lang.UUID;
import cn.hutool.extra.ftp.Ftp;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 FTP 的存储引擎实现。
 *
 * @author richie696
 * @since 2023-09-05
 */
@Slf4j
@Service("ftpStorageEngine")
@RequiredArgsConstructor
public class FtpStorageEngine extends AbstractDestroyEngine<Ftp> implements StorageEngine {

    /** 存储配置（含 FTP 连接信息） */
    private final StorageProperties properties;

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection) {
        var serialize = JsonUtils.getInstance().serialize(collection, true);
        Objects.requireNonNull(serialize, "The serialized string cannot be null.");
        var hashValue = HashUtils.sha256(serialize);
        var inputStream = IOUtils.toInputStream(serialize, StandardCharsets.UTF_8);
        return getUploadResponse(key, hashValue, inputStream);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection) {
        var serialize = JsonUtils.getInstance().serialize(collection, true);
        Objects.requireNonNull(serialize, "The serialized string cannot be null.");
        var hashValue = HashUtils.sha256(serialize);
        var inputStream = IOUtils.toInputStream(serialize, StandardCharsets.UTF_8);
        return getUploadResponse(key, hashValue, inputStream);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Object object) {
        var serialize = JsonUtils.getInstance().serialize(object, true);
        Objects.requireNonNull(serialize, "The serialized string cannot be null.");
        var hashValue = HashUtils.sha256(serialize);
        var inputStream = IOUtils.toInputStream(serialize, StandardCharsets.UTF_8);
        return getUploadResponse(key, hashValue, inputStream);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        try (var client = getClient(Ftp.class)) {
            var upload = client.upload(getDistPath(key), file);
            if (!upload) {
                return UploadResponse.builder()
                        .success(false)
                        .errorMessage("Failed to upload file to FTP server.")
                        .build();
            }
            return UploadResponse.builder()
                    .success(true)
                    .key(key)
                    .versionId(UUID.fastUUID().toString(true))
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (Exception e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        return getUploadResponse(key, null, inputStream);
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options) {
        throw new UnsupportedOperationException("Unsupported operation.");
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream, ImageOptions options) {
        throw new UnsupportedOperationException("Unsupported operation.");
    }

    @Override
    public <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeReference) {
        var distPath = getDistPath(key);
        distPath = distPath.substring(0, distPath.lastIndexOf("/"));
        var fileName = getTargetFIleName(key);
        var tempPath = System.getProperty("java.io.tmpdir") + File.separator + UUID.fastUUID().toString(true) + File.separator + fileName;
        var tempFile = new File(tempPath);
        try (var client = getClient(Ftp.class)) {
            log.debug(String.valueOf(tempFile.createNewFile()));
            client.download(distPath, fileName, tempFile);
            try (var fis = new FileInputStream(tempFile)) {
                T obj = JsonUtils.getInstance().deserialize(fis, new TypeReference<>() {
                });
                if (obj == null) {
                    return new DownloadResponse<T>()
                            .setSuccess(false)
                            .setErrorMessage("Failed to deserialize the file content.")
                            .setKey(key);
                }
                return new DownloadResponse<T>()
                        .setSuccess(true)
                        .setData(obj)
                        .setKey(key)
                        .setVersionId(UUID.fastUUID().toString(true))
                        .setContentMD5("")
                        .setContentType("application/json")
                        .setContentEncoding(StandardCharsets.UTF_8.name());
            }
        } catch (IOException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        if (!targetPath.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
        var distPath = getDistPath(key);
        distPath = distPath.substring(0, distPath.lastIndexOf("/"));
        var fileName = getTargetFIleName(key);
        try (var client = getClient(Ftp.class)) {
            client.download(distPath, fileName, targetPath);
            try (var fis = new FileInputStream(targetPath)) {
                byte[] bytes;
                if (returnData) {
                    bytes = fis.readAllBytes();
                } else {
                    bytes = new byte[0];
                }
                if (bytes.length == 0) {
                    return new DownloadResponse<byte[]>()
                            .setSuccess(false)
                            .setErrorMessage("Failed to deserialize the file content.")
                            .setKey(key);
                }
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setKey(key)
                        .setVersionId(UUID.fastUUID().toString(true))
                        .setContentMD5("")
                        .setContentType("application/octet-stream")
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setData(bytes);
            }
        } catch (IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        var targetFile = new File(targetPath);
        return getObject(key, targetFile, returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        try (var client = getClient(Ftp.class)) {
            return client.existFile(getDistPath(key));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        String ftpPath = getDistPath(key);
        return DirectUploadPolicy.builder()
                .success(true)
                .errorMessage("FTP 不支持标准对象存储预签名，返回可用兜底 FTP 路径。")
                .method("PUT")
                .uploadUrl(ftpPath)
                .headers(Map.of())
                .formFields(Map.of())
                .bucketName("ftp")
                .key(key)
                .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                .fallback(true)
                .build();
    }

    @Override
    public void destroy(@Nonnull Ftp client) {
        try {
            client.close();
        } catch (IOException _) {
        }
    }

    private String getDistPath(String key) {
        return properties.getFtp().getBasePath() + File.separator + key;
    }

    private String getTargetFIleName(String key) {
        if (key.contains("/")) {
            return key.substring(key.lastIndexOf("/") + 1);
        } else if (key.contains("\\")) {
            return key.substring(key.lastIndexOf("\\") + 2);
        } else {
            return key;
        }
    }

    private UploadResponse getUploadResponse(String key, String hashValue, InputStream inputStream) {
        var targetFileName = getTargetFIleName(key);
        try (var client = getClient(Ftp.class)) {
            var upload = client.upload(getDistPath(key), targetFileName, inputStream);
            if (!upload) {
                return UploadResponse.builder()
                        .success(false)
                        .errorMessage("Failed to upload file to FTP server.")
                        .build();
            }
            return UploadResponse.builder()
                    .success(true)
                    .key(key)
                    .versionId(UUID.fastUUID().toString(true))
                    .hashValue(hashValue)
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (IOException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
