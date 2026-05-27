package com.richie.component.storage.core.impl;

import com.richie.component.storage.core.StorageEngine;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.pool.SftpSessionPool;
import java.util.UUID;
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
@ConditionalOnBean(SftpSessionPool.class)
@RequiredArgsConstructor
public final class SftpStorageEngine implements StorageEngine {

    private final StorageProperties properties;
    private final SftpSessionPool sftpSessionPool;

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
            return getUploadResponse(key, fis.readAllBytes());
        } catch (IOException e) {
            return fail(e);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        key = getRealPath(key);
        try {
            return getUploadResponse(key, inputStream.readAllBytes());
        } catch (IOException e) {
            return fail(e);
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
        ChannelSftp client = null;
        try {
            client = acquire();
            try (var inputStream = client.get(key)) {
                if (inputStream == null) {
                    return new DownloadResponse<T>()
                            .setSuccess(false)
                            .setErrorMessage("The file does not exist.")
                            .setRequestId(uid()).setKey(key);
                }
                return new DownloadResponse<T>()
                        .setSuccess(true).setKey(key)
                        .setVersionId(uid())
                        .setContentType("application/json")
                        .setData(JsonUtils.getInstance().deserialize(inputStream, typeReference));
            }
        } catch (SftpException | IOException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false).setErrorMessage(e.getMessage())
                    .setRequestId(uid()).setKey(key);
        } finally {
            release(client);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        key = getRealPath(key);
        if (!targetPath.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setRequestId(uid()).setKey(key);
        }
        ChannelSftp client = null;
        try {
            client = acquire();
            client.get(key, targetPath.getAbsolutePath());
            if (returnData) {
                return new DownloadResponse<byte[]>()
                        .setSuccess(true).setKey(key)
                        .setRequestId(uid()).setVersionId("1")
                        .setContentType("application/octet-stream")
                        .setData(Files.readAllBytes(targetPath.toPath()));
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true).setKey(key)
                    .setRequestId(uid()).setVersionId("1")
                    .setContentType("application/octet-stream");
        } catch (SftpException | IOException e) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false).setErrorMessage(e.getMessage())
                    .setRequestId(uid()).setKey(key);
        } finally {
            release(client);
        }
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        return getObject(key, new File(targetPath), returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        key = getRealPath(key);
        ChannelSftp client = null;
        try {
            client = acquire();
            client.lstat(key);
            return true;
        } catch (SftpException e) {
            return false;
        } finally {
            release(client);
        }
    }

    private ChannelSftp acquire() {
        try {
            return sftpSessionPool.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to borrow SFTP session from pool", e);
        }
    }

    private void release(ChannelSftp client) {
        if (client != null) {
            sftpSessionPool.returnObject(client);
        }
    }

    private String getRealPath(String key) {
        return properties.getSftp().getBasePath() + File.separator + key;
    }

    private UploadResponse getUploadResponse(String key, byte[] serialize) {
        ChannelSftp client = null;
        try (var input = new ByteArrayInputStream(serialize)) {
            client = acquire();
            mkDirs(client, key);
            client.put(input, key);
            return UploadResponse.builder()
                    .success(true).key(key)
                    .versionId(uid())
                    .hashValue(HashUtils.sha256(new String(serialize)))
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (SftpException | IOException e) {
            return fail(e);
        } finally {
            release(client);
        }
    }

    private void mkDirs(ChannelSftp sftp, String dir) throws SftpException {
        var folders = dir.split("/");
        sftp.cd("/");
        for (var folder : folders) {
            if (!folder.isEmpty()) {
                try { sftp.cd(folder); }
                catch (SftpException e) { sftp.mkdir(folder); sftp.cd(folder); }
            }
        }
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static UploadResponse fail(Exception e) {
        return UploadResponse.builder()
                .success(false).errorMessage(e.getMessage())
                .requestId(uid()).key(null)
                .build();
    }

}
