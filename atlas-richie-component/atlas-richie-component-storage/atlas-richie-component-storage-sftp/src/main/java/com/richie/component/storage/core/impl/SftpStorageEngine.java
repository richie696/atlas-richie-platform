package com.richie.component.storage.core.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import cn.hutool.core.lang.UUID;
import tools.jackson.core.type.TypeReference;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service("sftpStorageEngine")
@ConditionalOnBean(ChannelSftp.class)
@RequiredArgsConstructor
public final class SftpStorageEngine extends AbstractDestroyEngine<ChannelSftp> {

    private final StorageProperties properties;

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection) {
        key = getRealPath(key);
        var serialize = JsonUtils.getInstance().serializeBytes(collection);
        Objects.requireNonNull(serialize, "The serialization result cannot be null.");
        return getUploadResponse(key, serialize);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection) {
        key = getRealPath(key);
        var serialize = JsonUtils.getInstance().serializeBytes(collection);
        Objects.requireNonNull(serialize, "The serialization result cannot be null.");
        return getUploadResponse(key, serialize);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Object object) {
        key = getRealPath(key);
        var serialize = JsonUtils.getInstance().serializeBytes(object);
        Objects.requireNonNull(serialize, "The serialization result cannot be null.");
        return getUploadResponse(key, serialize);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        key = getRealPath(key);
        try (var fis = new FileInputStream(file)) {
            var allBytes = fis.readAllBytes();
            return getUploadResponse(key, allBytes);
        } catch (IOException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .requestId(UUID.fastUUID().toString(true))
                    .key(key)
                    .build();
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        key = getRealPath(key);
        try {
            var allBytes = inputStream.readAllBytes();
            return getUploadResponse(key, allBytes);
        } catch (IOException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .requestId(UUID.fastUUID().toString(true))
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
        var client = getClient(ChannelSftp.class);
        try (var inputStream = client.get(getRealPath(key))) {
            if (inputStream == null) {
                return new DownloadResponse<T>()
                        .setSuccess(false)
                        .setErrorMessage("The file does not exist.")
                        .setRequestId(UUID.fastUUID().toString(true))
                        .setKey(key);
            }
            return new DownloadResponse<T>()
                    .setSuccess(true)
                    .setKey(key)
                    .setVersionId(UUID.fastUUID().toString(true))
                    .setContentType("application/json")
                    .setData(JsonUtils.getInstance().deserialize(inputStream, typeReference));
        } catch (SftpException | IOException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(UUID.fastUUID().toString(true))
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
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
        var client = getClient(ChannelSftp.class);
        try {
            client.get(getRealPath(key), targetPath.getAbsolutePath());
            if (returnData) {
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setKey(key)
                        .setRequestId(UUID.fastUUID().toString(true))
                        .setVersionId("1")
                        .setContentType("application/octet-stream")
                        .setData(Files.readAllBytes(targetPath.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true)
                    .setKey(key)
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setVersionId("1")
                    .setContentType("application/octet-stream");
        } catch (SftpException | IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .setRequestId(UUID.fastUUID().toString(true))
                    .setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        key = getRealPath(key);
        return getObject(key, new File(targetPath), returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        var client = getClient(ChannelSftp.class);
        try {
            client.lstat(getRealPath(key));
            return true;
        } catch (SftpException e) {
            return false;
        }
    }

    @Override
    public void destroy(@Nonnull ChannelSftp sftpClient) {
        sftpClient.disconnect();
    }

    private String getRealPath(String key) {
        return properties.getSftp().getBasePath() + File.separator + key;
    }

    private UploadResponse getUploadResponse(String key, byte[] serialize) {
        try (var byteArrayInputStream = new ByteArrayInputStream(serialize)) {
            var filePath = getRealPath(key);
            var client = getClient(ChannelSftp.class);
            mkDirs(client, filePath);
            client.put(byteArrayInputStream, filePath);
            return UploadResponse.builder()
                    .success(true)
                    .key(key)
                    .versionId(UUID.fastUUID().toString(true))
                    .hashValue(HashUtils.sha256(new String(serialize)))
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (SftpException | IOException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .requestId(UUID.fastUUID().toString(true))
                    .key(key)
                    .build();
        }
    }

    private void mkDirs(ChannelSftp sftp, String dir) throws SftpException {
        var folders = dir.split("/");
        sftp.cd("/");
        for (var folder : folders) {
            if (!folder.isEmpty()) {
                try {
                    sftp.cd(folder);
                } catch (Exception e) {
                    sftp.mkdir(folder);
                    sftp.cd(folder);
                }
            }
        }
    }
}
