package com.richie.component.storage.local.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.richie.component.cache.GlobalCache;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.impl.AbstractDestroyEngine;
import com.richie.component.storage.local.repository.entity.FileMetadata;
import com.richie.component.storage.local.repository.mapper.FileMetadataMapper;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.security.HashUtils;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 本地文件存储引擎
 *
 * <p>提供对象上传/下载/存在性检测等能力，内置：
 * <ul>
 *   <li>路径安全校验与原子落盘</li>
 *   <li>大小限制与内容指纹（SHA-256）计算</li>
 *   <li>同内容指纹去重（已存在且指纹一致时不重复写盘）</li>
 *   <li>基于全局缓存的文件存在性/内容/元数据缓存</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-14
 */
@Slf4j
@Service("localStorageEngine")
@RequiredArgsConstructor
public final class LocalStorageEngine extends AbstractDestroyEngine<Void> {

    private final StorageProperties properties;
    private final LocalConfig localConfig;
    private final FileMetadataMapper fileMetadataMapper;

    // 缓存键前缀
    private static final String FILE_EXISTS_PREFIX = "file:exists:";
    private static final String FILE_METADATA_PREFIX = "file:metadata:";
    private static final String FILE_CONTENT_PREFIX = "file:content:";

    // 缓存过期时间（毫秒）
    private static final long FILE_EXISTS_TTL = 3600000; // 1小时
    private static final long FILE_METADATA_TTL = 1800000; // 30分钟
    private static final long FILE_CONTENT_TTL = 600000; // 10分钟

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection) {
        var content = JsonUtils.getInstance().serialize(collection, true);
        Objects.requireNonNull(content);
        return getUploadResponse(key, content, false);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection) {
        var content = JsonUtils.getInstance().serialize(collection, true);
        Objects.requireNonNull(content);
        return getUploadResponse(key, content, false);
    }

    @Override
    public UploadResponse putData(@Nonnull String key, @Nonnull Object object) {
        var content = JsonUtils.getInstance().serialize(object, true);
        Objects.requireNonNull(content);
        return getUploadResponse(key, content, false);
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull File file) {
        try (var fileInputStream = new FileInputStream(file)) {
            return getUploadResponse(key, fileInputStream, true);
        } catch (IOException e) {
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream) {
        return getUploadResponse(key, inputStream, true);
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream, ImageOptions options) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeReference) {
        // 1. 先查缓存
        String cacheKey = FILE_CONTENT_PREFIX + key;
        String cachedContent = GlobalCache.value().get(cacheKey, String.class);
        if (cachedContent != null) {
            try {
                T data = JsonUtils.getInstance().deserialize(cachedContent, typeReference);
                if (data != null) {
                    var success = new DownloadResponse<T>()
                            .setData(data);
                    success.setSuccess(true)
                            .setKey(key)
                            .setVersionId(UUID.randomUUID().toString().replace("-", ""))
                            .setContentMD5(HashUtils.md5(cachedContent))
                            .setContentType("application/json")
                            .setContentEncoding(StandardCharsets.UTF_8.name());
                    return success;
                }
            } catch (Exception e) {
                log.warn("Failed to deserialize cached content for key: {}", key, e);
            }
            log.warn("Corrupted cache or deserialization failed for key: {}, falling back to filesystem", key);
            // 缓存数据损坏，继续从文件系统读取
        }

        // 2. 从文件系统读取
        var absolutePath = getAbsolutePath(key);
        if (Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS)) {
            try {
                var content = Files.readString(absolutePath, StandardCharsets.UTF_8);

                // 3. 更新缓存（只缓存小文件）
                if (content.length() <= 1024 * 1024) { // 1MB以下
                    GlobalCache.value().set(cacheKey, content, FILE_CONTENT_TTL);
                }

                var success = new DownloadResponse<T>()
                        .setData(JsonUtils.getInstance().deserialize(content, typeReference));
                success.setSuccess(true)
                        .setKey(key)
                        .setVersionId(UUID.randomUUID().toString().replace("-", ""))
                        .setContentMD5(HashUtils.md5(content))
                        .setContentType("application/json")
                        .setContentEncoding(StandardCharsets.UTF_8.name());
                return success;
            } catch (IOException e) {
                log.error("Read file error.", e);
                DownloadResponse<T> error = new DownloadResponse<>();
                error.setSuccess(false).setErrorMessage(e.getMessage());
                return error;
            }
        }
        DownloadResponse<T> error = new DownloadResponse<>();
        error.setSuccess(false).setErrorMessage("File not found.");
        return error;
    }

    @Override
    public DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData) {
        if (!targetPath.canWrite()) {
            return new DownloadResponse<byte[]>()
                    .setSuccess(false)
                    .setErrorMessage("The directory does not have permission to write to files.")
                    .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                    .setKey(key);
        }
        var absolutePath = getAbsolutePath(key);
        if (Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS)) {
            try {
                return new DownloadResponse<byte[]>()
                        .setSuccess(true)
                        .setKey(key)
                        .setRequestId(UUID.randomUUID().toString().replace("-", ""))
                        .setVersionId("1")
                        .setContentType("application/octet-stream")
                        .setData(Files.readAllBytes(absolutePath));
            } catch (IOException e) {
                return new DownloadResponse<byte[]>().setSuccess(false).setErrorMessage(e.getMessage());
            }
        }
        return new DownloadResponse<byte[]>().setSuccess(false).setErrorMessage("File not found.");
    }

    @Override
    public DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData) {
        return getObject(key, new File(targetPath), returnData);
    }

    @Override
    public boolean existsObject(@Nonnull String key) {
        // 1. 先查缓存
        String cacheKey = FILE_EXISTS_PREFIX + key;
        Boolean exists = GlobalCache.value().get(cacheKey, Boolean.class);
        if (exists != null) {
            return exists;
        }

        // 2. 查文件系统
        var absolutePath = getAbsolutePath(key);
        boolean fileExists = Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS);

        // 3. 更新缓存
        GlobalCache.value().set(cacheKey, fileExists, FILE_EXISTS_TTL);

        return fileExists;
    }

    private Path getAbsolutePath(String key) {
        var path = properties.getLocal().getPath();
        if (StringUtils.isNotBlank(path) && path.startsWith(".")) {
            path = Objects.requireNonNull(this.getClass().getResource("/")).getPath().substring(1) + path.substring(2);
        }
        return Paths.get(path + File.separator + key);
    }

    private UploadResponse getUploadResponse(String key, Object content, boolean isFile) {
        // 路径规范化与校验，防目录穿越
        if (!isSafeKey(key)) {
            return UploadResponse.builder().success(false).errorMessage("Invalid key: path traversal detected").build();
        }
        var absolutePath = getAbsolutePath(key);
        String hashValue = null;
        try {
            Files.createDirectories(absolutePath.getParent());

            if (!isFile && content instanceof String str) {
                // 大小限制（字符串）
                long maxSize = getContentMaxSize();
                if (str.getBytes(StandardCharsets.UTF_8).length > maxSize) {
                    return UploadResponse.builder().success(false).errorMessage("File too large").build();
                }
                hashValue = HashUtils.sha256(str);
                // 如果目标已存在且指纹相同，则不再落盘，直接返回
                if (Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS)) {
                    String existed = Files.readString(absolutePath, StandardCharsets.UTF_8);
                    String existedHash = HashUtils.sha256(existed);
                    if (hashValue.equalsIgnoreCase(existedHash)) {
                        updateFileCaches(key, existed, hashValue);
                        return UploadResponse.builder()
                                .success(true)
                                .key(key)
                                .versionId(UUID.randomUUID().toString().replace("-", ""))
                                .hashValue(hashValue)
                                .uploadTime(OffsetDateTime.now())
                                .build();
                    }
                }
                Files.writeString(absolutePath, str, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                updateFileCaches(key, str, hashValue);
                // 写入/更新元数据
                upsertMetadata(key, null, "application/json", Files.size(absolutePath), hashValue, absolutePath.toString());
            } else if (content instanceof InputStream is) {
                // 流式写入到临时文件并计算SHA-256与大小
                var tmp = Files.createTempFile(absolutePath.getParent(), ".upload-", ".tmp");
                long maxSize = getContentMaxSize();
                long written = 0L;
                MessageDigest md = sha256();
                try (DigestInputStream dis = new DigestInputStream(is, md); OutputStream os = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = dis.read(buf)) != -1) {
                        written += n;
                        if (written > maxSize) {
                            dis.close();
                            os.close();
                            Files.deleteIfExists(tmp);
                            return UploadResponse.builder().success(false).errorMessage("File too large").build();
                        }
                        os.write(buf, 0, n);
                    }
                    os.flush();
                }
                hashValue = HexFormat.of().formatHex(md.digest());
                // 如果目标已存在且指纹相同，则不再落盘
                if (Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS)) {
                    String existedHash = computeSha256(absolutePath);
                    if (hashValue.equalsIgnoreCase(existedHash)) {
                        Files.deleteIfExists(tmp);
                        updateFileExistsCache(key, true);
                        String metadataCacheKey = FILE_METADATA_PREFIX + key;
                        Map<String, Object> metadata = Map.of(
                                "key", key,
                                "hashValue", hashValue,
                                "size", Files.size(absolutePath),
                                "uploadTime", LocalDateTime.now().toString()
                        );
                        GlobalCache.struct().set(metadataCacheKey, metadata, FILE_METADATA_TTL);
                        return UploadResponse.builder()
                                .success(true)
                                .key(key)
                                .versionId(UUID.randomUUID().toString().replace("-", ""))
                                .hashValue(hashValue)
                                .uploadTime(OffsetDateTime.now())
                                .build();
                    }
                }
                // 原子替换
                try {
                    Files.move(tmp, absolutePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, absolutePath, StandardCopyOption.REPLACE_EXISTING);
                }
                updateFileExistsCache(key, true);
                // 更新元数据缓存（仅大小与hash）
                String metadataCacheKey = FILE_METADATA_PREFIX + key;
                Map<String, Object> metadata = Map.of(
                        "key", key,
                        "hashValue", hashValue,
                        "size", Files.size(absolutePath),
                        "uploadTime", LocalDateTime.now().toString()
                );
                GlobalCache.struct().set(metadataCacheKey, metadata, FILE_METADATA_TTL);
                // 写入/更新元数据
                upsertMetadata(key, null, "application/octet-stream", Files.size(absolutePath), hashValue, absolutePath.toString());
            }
            return UploadResponse.builder()
                    .success(true)
                    .key(key)
                    .versionId(UUID.randomUUID().toString().replace("-", ""))
                    .hashValue(hashValue)
                    .uploadTime(OffsetDateTime.now())
                    .build();
        } catch (IOException e) {
            log.error("Write file error.", e);
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private boolean isSafeKey(String key) {
        // 禁止绝对路径、盘符、父目录跳转
        return !key.contains("..") && !key.contains(":") && !key.startsWith("/") && !key.startsWith("\\");
    }

    private long getContentMaxSize() {
        try {
            return localConfig != null && localConfig.getCache() != null && localConfig.getCache().getContentMaxSize() != null
                    ? localConfig.getCache().getContentMaxSize()
                    : 1_048_576L;
        } catch (Exception e) {
            return 1_048_576L;
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String computeSha256(Path path) throws IOException {
        MessageDigest md = sha256();
        try (InputStream in = Files.newInputStream(path);
             DigestInputStream dis = new DigestInputStream(in, md)) {
            dis.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /**
     * 将文件元数据写入或更新到数据库
     * @param key 存储键
     * @param originalName 原始文件名（可空）
     * @param contentType 内容类型
     * @param sizeBytes 大小
     * @param hashValue 指纹
     * @param physicalPath 物理路径
     */
    private void upsertMetadata(String key, String originalName, String contentType, long sizeBytes, String hashValue, String physicalPath) {
        try {
            Long exists = fileMetadataMapper.selectCount(new LambdaQueryWrapper<FileMetadata>().eq(FileMetadata::getKeyPath, key));
            var now = LocalDateTime.now();
            var versionId = UUID.randomUUID().toString().replace("-", "");
            if (exists != null && exists > 0L) {
                FileMetadata update = new FileMetadata();
                update.setSizeBytes(sizeBytes);
                update.setHashValue(hashValue);
                update.setVersionId(versionId);
                update.setPhysicalPath(physicalPath);
                update.setUploadTime(now);
                fileMetadataMapper.update(update, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FileMetadata>()
                        .eq(FileMetadata::getKeyPath, key));
            } else {
                FileMetadata entity = new FileMetadata();
                entity.setKeyPath(key);
                entity.setOriginalName(originalName == null ? "" : originalName);
                entity.setContentType(contentType);
                entity.setSizeBytes(sizeBytes);
                entity.setHashValue(hashValue);
                entity.setVersionId(versionId);
                entity.setPhysicalPath(physicalPath);
                entity.setUploadTime(now);
                entity.setAccessCount(0L);
                entity.setStorageState("ACTIVE");
                fileMetadataMapper.insert(entity);
            }
        } catch (Exception e) {
            log.warn("Upsert metadata failed: key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 更新文件相关缓存
     */
    private void updateFileCaches(String key, String content, String hashValue) {
        // 更新文件存在性缓存
        updateFileExistsCache(key, true);

        // 更新文件内容缓存（只缓存小文件）
        if (content.length() <= 1024 * 1024) { // 1MB以下
            String contentCacheKey = FILE_CONTENT_PREFIX + key;
            GlobalCache.value().set(contentCacheKey, content, FILE_CONTENT_TTL);
        }

        // 更新文件元数据缓存
        String metadataCacheKey = FILE_METADATA_PREFIX + key;
        Map<String, Object> metadata = Map.of(
            "key", key,
            "hashValue", hashValue,
            "size", content.length(),
            "uploadTime", OffsetDateTime.now().toString(),
            "contentType", "application/json"
        );
        GlobalCache.struct().set(metadataCacheKey, metadata, FILE_METADATA_TTL);
    }

    /**
     * 更新文件存在性缓存
     */
    private void updateFileExistsCache(String key, boolean exists) {
        String existsCacheKey = FILE_EXISTS_PREFIX + key;
        GlobalCache.value().set(existsCacheKey, exists, FILE_EXISTS_TTL);
    }

    /**
     * 清理文件相关缓存
     */
    public void clearFileCaches(String key) {
        String existsCacheKey = FILE_EXISTS_PREFIX + key;
        String contentCacheKey = FILE_CONTENT_PREFIX + key;
        String metadataCacheKey = FILE_METADATA_PREFIX + key;

        GlobalCache.key().removeCache(existsCacheKey);
        GlobalCache.key().removeCache(contentCacheKey);
        GlobalCache.key().removeCache(metadataCacheKey);

        log.debug("Cleared caches for file: {}", key);
    }

    /**
     * 批量清理文件缓存
     */
    public void clearFileCachesBatch(java.util.Collection<String> keys) {
        keys.forEach(this::clearFileCaches);
    }
}
