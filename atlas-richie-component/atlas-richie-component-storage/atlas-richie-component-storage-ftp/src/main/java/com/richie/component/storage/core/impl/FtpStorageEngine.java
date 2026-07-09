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

import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.pool.FtpClientPool;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service("ftpStorageEngine")
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class FtpStorageEngine extends AbstractDestroyEngine<FTPClient> implements StorageEngine {

    private final StorageProperties properties;
    private final FtpClientPool ftpClientPool;

    // ====== Upload Data ======

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection) {
        var json = serialize(collection);
        return upload(key, json, HashUtils.sha256(json));
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection) {
        var json = serialize(collection);
        return upload(key, json, HashUtils.sha256(json));
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Object object) {
        var json = serialize(object);
        return upload(key, json, HashUtils.sha256(json));
    }

    // ====== Upload File ======

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        var remoteDir = dir(key);
        var remoteName = name(key);
        FTPClient client = null;
        try {
            client = acquire();
            ensureDir(client, remoteDir);
            try (var input = new FileInputStream(file)) {
                if (!client.storeFile(remoteDir + "/" + remoteName, input)) {
                    return fail("FTP storeFile failed, reply: " + client.getReplyString());
                }
            }
            return ok(key);
        } catch (IOException e) {
            return fail(e);
        } finally {
            release(client);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        return upload(key, null, inputStream);
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream, ImageOptions options) {
        throw new UnsupportedOperationException();
    }

    // ====== Download ======

    @Override
    public <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeRef) {
        var remoteDir = dir(key);
        var remoteName = name(key);
        FTPClient client = null;
        try {
            client = acquire();
            var tempFile = File.createTempFile("ftp-", ".tmp");
            try (var output = new FileOutputStream(tempFile)) {
                if (!client.retrieveFile(remoteDir + "/" + remoteName, output)) {
                    return new DownloadResponse<T>()
                            .setSuccess(false)
                            .setErrorMessage("FTP retrieveFile failed, reply: " + client.getReplyString())
                            .setKey(key);
                }
            }
            try (var fis = new FileInputStream(tempFile)) {
                T obj = JsonUtils.getInstance().deserialize(fis, new TypeReference<>() {});
                if (obj == null) {
                    return new DownloadResponse<T>()
                            .setSuccess(false)
                            .setErrorMessage("Failed to deserialize the file content.")
                            .setKey(key);
                }
                return new DownloadResponse<T>()
                        .setSuccess(true).setData(obj).setKey(key)
                        .setVersionId(uid()).setContentMD5("")
                        .setContentType("application/json")
                        .setContentEncoding(StandardCharsets.UTF_8.name());
            }
        } catch (IOException e) {
            return new DownloadResponse<T>()
                    .setSuccess(false).setErrorMessage(e.getMessage())
                    .setRequestId(uid()).setKey(key);
        } finally {
            release(client);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        if (!targetPath.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setRequestId(uid()).setKey(key);
        }
        var remoteDir = dir(key);
        var remoteName = name(key);
        FTPClient client = null;
        try {
            client = acquire();
            try (var output = new FileOutputStream(targetPath)) {
                if (!client.retrieveFile(remoteDir + "/" + remoteName, output)) {
                    return new DownloadResponse<byte[]>()
                            .setSuccess(false)
                            .setErrorMessage("FTP retrieveFile failed, reply: " + client.getReplyString())
                            .setKey(key);
                }
            }
            byte[] bytes;
            if (returnData) {
                try (var fis = new FileInputStream(targetPath)) {
                    bytes = fis.readAllBytes();
                }
            } else {
                bytes = new byte[0];
            }
            return new DownloadResponse<byte[]>()
                    .setSuccess(true).setKey(key)
                    .setVersionId(uid()).setContentMD5("")
                    .setContentType("application/octet-stream")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setData(bytes);
        } catch (IOException e) {
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

    // ====== Misc ======

    @Override
    public boolean existsObject(@Nonnull String key) {
        var remoteDir = dir(key);
        var remoteName = name(key);
        FTPClient client = null;
        try {
            client = acquire();
            client.changeWorkingDirectory(remoteDir);
            return client.listFiles(remoteName).length > 0;
        } catch (IOException e) {
            return false;
        } finally {
            release(client);
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpire = Math.max(expireSeconds, 60);
        return DirectUploadPolicy.builder()
                .success(true)
                .errorMessage("FTP does not support presigned URLs.")
                .method("PUT")
                .uploadUrl(dir(key) + "/" + name(key))
                .headers(Map.of())
                .formFields(Map.of())
                .bucketName("ftp")
                .key(key)
                .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                .fallback(true)
                .build();
    }

    // ====== Lifecycle ======

    @Override
    public void destroy(FTPClient client) {
        try { client.logout(); } catch (IOException ignored) { }
        try { client.disconnect(); } catch (IOException ignored) { }
    }

    // ====== Internal helpers ======

    private FTPClient acquire() {
        try {
            return ftpClientPool.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to borrow FTP client from pool", e);
        }
    }

    private void release(FTPClient client) {
        if (client != null) {
            ftpClientPool.returnObject(client);
        }
    }

    private String serialize(Object obj) {
        var json = JsonUtils.getInstance().serialize(obj, true);
        Objects.requireNonNull(json, "The serialized string cannot be null.");
        return json;
    }

    private UploadResponse upload(String key, String hashValue, InputStream inputStream) {
        var remoteDir = dir(key);
        var remoteName = name(key);
        FTPClient client = null;
        try {
            client = acquire();
            ensureDir(client, remoteDir);
            if (!client.storeFile(remoteDir + "/" + remoteName, inputStream)) {
                return fail("FTP storeFile failed, reply: " + client.getReplyString());
            }
            return UploadResponse.builder()
                    .success(true).key(key)
                    .versionId(uid()).hashValue(hashValue)
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (IOException e) {
            return fail(e);
        } finally {
            release(client);
        }
    }

    private UploadResponse upload(String key, String json, String hash) {
        return upload(key, hash, IOUtils.toInputStream(json, StandardCharsets.UTF_8));
    }

    private void ensureDir(FTPClient client, String path) throws IOException {
        if (path == null || path.isEmpty() || path.equals("/")) return;
        var parts = path.split("/");
        var cursor = "";
        for (var part : parts) {
            if (part.isEmpty()) continue;
            cursor += "/" + part;
            if (!client.changeWorkingDirectory(cursor)) {
                client.makeDirectory(cursor);
                client.changeWorkingDirectory(cursor);
            }
        }
        client.changeWorkingDirectory("/");
    }

    private String base() {
        var b = properties.getFtp().getBasePath();
        return b.endsWith("/") ? b : b + "/";
    }

    private String dir(String key) {
        var idx = key.lastIndexOf("/");
        return base() + (idx > 0 ? key.substring(0, idx) : "");
    }

    private String name(String key) {
        var idx = key.lastIndexOf("/");
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static UploadResponse ok(String key) {
        return UploadResponse.builder()
                .success(true).key(key)
                .versionId(uid())
                .uploadTime(OffsetDateTime.now())
                .build();
    }

    private static UploadResponse fail(String msg) {
        return UploadResponse.builder().success(false).errorMessage(msg).build();
    }

    private static UploadResponse fail(IOException e) {
        return UploadResponse.builder().success(false).errorMessage(e.getMessage()).build();
    }

}
