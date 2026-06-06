package com.richie.component.storage.core.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import tools.jackson.core.type.TypeReference;
import org.codelibs.jcifs.smb.CIFSContext;
import org.codelibs.jcifs.smb.impl.SmbFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service("smbStorageEngine")
@ConditionalOnBean(CIFSContext.class)
@RequiredArgsConstructor
public final class SmbStorageEngine implements StorageEngine {

    private final StorageProperties properties;
    private final CIFSContext cifsContext;

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
        try (var smbFile = new SmbFile(smbUrl(key), cifsContext);
             var out = smbFile.getOutputStream();
             var in = new FileInputStream(file)) {
            in.transferTo(out);
            return ok(key);
        } catch (Exception e) {
            log.error("Failed to upload file to SMB: key={}", key, e);
            return fail(key, e);
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        try (var smbFile = new SmbFile(smbUrl(key), cifsContext);
             var out = smbFile.getOutputStream()) {
            inputStream.transferTo(out);
            return ok(key);
        } catch (Exception e) {
            log.error("Failed to upload stream to SMB: key={}", key, e);
            return fail(key, e);
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
        try (var smbFile = new SmbFile(smbUrl(key), cifsContext);
             var in = smbFile.getInputStream()) {
            var bytes = in.readAllBytes();
            return new DownloadResponse<T>()
                    .setSuccess(true).setKey(key)
                    .setVersionId(uid()).setContentMD5("")
                    .setContentType("application/json")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setData(JsonUtils.getInstance().deserialize(new ByteArrayInputStream(bytes), typeReference));
        } catch (Exception e) {
            log.error("Failed to get SMB data: key={}", key, e);
            return new DownloadResponse<T>()
                    .setSuccess(false).setErrorMessage(e.getMessage())
                    .setRequestId(uid()).setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        try (var smbFile = new SmbFile(smbUrl(key), cifsContext);
             var in = smbFile.getInputStream();
             var out = new FileOutputStream(targetPath)) {
            in.transferTo(out);
            byte[] data = returnData ? Files.readAllBytes(targetPath.toPath()) : null;
            return new DownloadResponse<byte[]>()
                    .setSuccess(true).setKey(key)
                    .setVersionId(uid()).setContentMD5("")
                    .setContentType("application/octet-stream")
                    .setData(data);
        } catch (Exception e) {
            log.error("Failed to get SMB object: key={}, target={}", key, targetPath, e);
            return new DownloadResponse<byte[]>()
                    .setSuccess(false).setErrorMessage(e.getMessage())
                    .setRequestId(uid()).setKey(key);
        }
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        return getObject(key, new File(targetPath), returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        try (var smbFile = new SmbFile(smbUrl(key), cifsContext)) {
            return smbFile.exists();
        } catch (Exception e) {
            log.error("Failed to check SMB existence: key={}", key, e);
            return false;
        }
    }

    @Override
    public DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        return DirectUploadPolicy.builder()
                .success(true)
                .errorMessage("SMB does not support presigned URLs.")
                .method("PUT")
                .uploadUrl(smbUrl(key))
                .headers(Map.of())
                .formFields(Map.of())
                .bucketName("smb")
                .key(key)
                .expireAt(OffsetDateTime.now().plusSeconds(Math.max(expireSeconds, 60)))
                .fallback(true)
                .build();
    }

    private String smbUrl(String key) {
        var cfg = properties.getSmb3();
        var base = cfg.getBasePath();
        if (!base.startsWith("/")) base = "/" + base;
        if (!base.endsWith("/")) base = base + "/";
        var host = cfg.getDomain();
        if (host == null || host.isBlank()) host = "localhost";
        return "smb://" + host + base + key;
    }

    private UploadResponse upload(String key, byte[] serialized) {
        var hash = HashUtils.sha256(new String(serialized, StandardCharsets.UTF_8));
        try (var smbFile = new SmbFile(smbUrl(key), cifsContext);
             var out = smbFile.getOutputStream();
             var in = new ByteArrayInputStream(serialized)) {
            in.transferTo(out);
            return UploadResponse.builder()
                    .success(true).key(key)
                    .versionId(uid()).hashValue(hash)
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Failed to upload data to SMB: key={}", key, e);
            return UploadResponse.builder()
                    .success(false).errorMessage(e.getMessage()).key(key)
                    .build();
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

    private static UploadResponse fail(String key, Exception e) {
        return UploadResponse.builder()
                .success(false).errorMessage(e.getMessage()).key(key)
                .build();
    }

}
