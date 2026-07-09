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
import com.richie.component.storage.pool.SftpSessionPool;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.OpenMode;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service("sftpStorageEngine")
@ConditionalOnBean(SftpSessionPool.class)
@ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public final class SftpStorageEngine implements StorageEngine {

    private static final int BUFFER_SIZE = 65536;
    private static final EnumSet<OpenMode> READ_MODE = EnumSet.of(OpenMode.Read);
    private static final EnumSet<OpenMode> WRITE_MODE = EnumSet.of(OpenMode.Write, OpenMode.Create, OpenMode.Truncate);

    private final StorageProperties properties;
    private final SftpSessionPool sessionPool;

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection) {
        return upload(key, serialize(collection));
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection) {
        return upload(key, serialize(collection));
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Object object) {
        return upload(key, serialize(object));
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        var remote = realPath(key);
        ClientSession session = null;
        try {
            session = acquire();
            try (var sftp = SftpClientFactory.instance().createSftpClient(session)) {
                ensureDir(sftp, remote, new HashSet<>());
                try (var in = new FileInputStream(file)) {
                    sftp.put(in, BUFFER_SIZE, remote, WRITE_MODE);
                }
            }
            return ok(key);
        } catch (Exception e) {
            log.error("Failed to upload file to SFTP: key={}", key, e);
            return fail(e);
        } finally {
            release(session);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        var remote = realPath(key);
        ClientSession session = null;
        try {
            session = acquire();
            try (var sftp = SftpClientFactory.instance().createSftpClient(session)) {
                ensureDir(sftp, remote, new HashSet<>());
                sftp.put(inputStream, BUFFER_SIZE, remote, WRITE_MODE);
            }
            return ok(key);
        } catch (Exception e) {
            log.error("Failed to upload stream to SFTP: key={}", key, e);
            return fail(e);
        } finally {
            release(session);
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
    public <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeRef) {
        var remote = realPath(key);
        ClientSession session = null;
        try {
            session = acquire();
            try (var sftp = SftpClientFactory.instance().createSftpClient(session);
                 var in = sftp.read(remote, BUFFER_SIZE, READ_MODE)) {
                var bytes = in.readAllBytes();
                return new DownloadResponse<T>()
                        .setSuccess(true).setKey(key)
                        .setVersionId(uid()).setContentMD5("")
                        .setContentType("application/json")
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setData(JsonUtils.getInstance().deserialize(new ByteArrayInputStream(bytes), typeRef));
            }
        } catch (Exception e) {
            log.error("Failed to get SFTP data: key={}", key, e);
            return new DownloadResponse<T>()
                    .setSuccess(false).setErrorMessage(e.getMessage())
                    .setRequestId(uid()).setKey(key);
        } finally {
            release(session);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        var remote = realPath(key);
        ClientSession session = null;
        try {
            session = acquire();
            try (var sftp = SftpClientFactory.instance().createSftpClient(session);
                 var in = sftp.read(remote, BUFFER_SIZE, READ_MODE);
                 var out = new FileOutputStream(targetPath)) {
                in.transferTo(out);
            }
            byte[] data = returnData ? java.nio.file.Files.readAllBytes(targetPath.toPath()) : null;
            return new DownloadResponse<byte[]>()
                    .setSuccess(true).setKey(key)
                    .setVersionId(uid()).setContentMD5("")
                    .setContentType("application/octet-stream")
                    .setData(data);
        } catch (Exception e) {
            log.error("Failed to get SFTP object: key={}", key, e);
            return new DownloadResponse<byte[]>()
                    .setSuccess(false).setErrorMessage(e.getMessage())
                    .setRequestId(uid()).setKey(key);
        } finally {
            release(session);
        }
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        return getObject(key, new File(targetPath), returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        ClientSession session = null;
        try {
            session = acquire();
            try (var sftp = SftpClientFactory.instance().createSftpClient(session)) {
                sftp.lstat(realPath(key));
                return true;
            }
        } catch (Exception e) {
            return false;
        } finally {
            release(session);
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        return DirectUploadPolicy.builder()
                .success(true)
                .errorMessage("SFTP does not support presigned URLs.")
                .method("PUT")
                .uploadUrl(realPath(key))
                .headers(Map.of())
                .formFields(Map.of())
                .bucketName("sftp")
                .key(key)
                .expireAt(OffsetDateTime.now().plusSeconds(Math.max(expireSeconds, 60)))
                .fallback(true)
                .build();
    }

    private ClientSession acquire() {
        try {
            return sessionPool.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to borrow SFTP session from pool", e);
        }
    }

    private void release(ClientSession session) {
        if (session == null) return;
        if (session.isOpen()) {
            sessionPool.returnObject(session);
        } else {
            try {
                sessionPool.invalidateObject(session);
            } catch (Exception ignored) {
            }
        }
    }

    private String realPath(String key) {
        return properties.getSftp().getBasePath() + "/" + key;
    }

    private UploadResponse upload(String key, byte[] serialized) {
        var hash = HashUtils.sha256(new String(serialized, StandardCharsets.UTF_8));
        var remote = realPath(key);
        ClientSession session = null;
        try {
            session = acquire();
            try (var sftp = SftpClientFactory.instance().createSftpClient(session)) {
                ensureDir(sftp, remote, new HashSet<>());
                try (var in = new ByteArrayInputStream(serialized)) {
                    sftp.put(in, BUFFER_SIZE, remote, WRITE_MODE);
                }
            }
            return UploadResponse.builder()
                    .success(true).key(key)
                    .versionId(uid()).hashValue(hash)
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Failed to upload data to SFTP: key={}", key, e);
            return UploadResponse.builder()
                    .success(false).errorMessage(e.getMessage()).key(key)
                    .build();
        } finally {
            release(session);
        }
    }

    private void ensureDir(SftpClient sftp, String remote, Set<String> knownDirs) throws IOException {
        var idx = remote.lastIndexOf("/");
        if (idx <= 0) return;
        var dir = remote.substring(0, idx);
        var parts = dir.split("/");
        var cursor = "";
        for (var part : parts) {
            if (part.isEmpty()) continue;
            cursor += "/" + part;
            if (knownDirs.contains(cursor)) continue;
            try {
                sftp.lstat(cursor);
                knownDirs.add(cursor);
            } catch (IOException e) {
                sftp.mkdir(cursor);
                knownDirs.add(cursor);
            }
        }
    }

    private byte[] serialize(Object obj) {
        var json = JsonUtils.getInstance().serializeBytes(obj);
        Objects.requireNonNull(json, "The serialized string cannot be null.");
        return json;
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

    private static UploadResponse fail(Exception e) {
        return UploadResponse.builder()
                .success(false).errorMessage(e.getMessage())
                .build();
    }

}
