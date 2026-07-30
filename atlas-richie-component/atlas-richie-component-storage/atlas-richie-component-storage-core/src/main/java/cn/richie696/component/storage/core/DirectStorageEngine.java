/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.richie696.component.storage.core;

import cn.richie696.component.storage.bean.DirectDownloadPolicy;
import cn.richie696.component.storage.bean.DirectUploadPolicy;
import cn.richie696.component.storage.bean.DirectUploadRequest;
import cn.richie696.component.storage.bean.DownloadResponse;
import cn.richie696.component.storage.bean.ObjectStatResponse;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 面向客户端直传、直读及上传确认的对象存储能力。
 * <p>
 * 调用方先申请直传策略，上传完成后通过 {@link #statObject(String)} 确认对象的实际元数据；需要对
 * 对象内容执行校验、识别或扫描时，使用 {@link #readObject(String, ObjectStreamConsumer)} 流式读取。
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 16:56:45
 */
public interface DirectStorageEngine {

    /**
     * 生成客户端直传对象存储的统一策略（预签名 URL 或可用兜底链接）。
     *
     * @param key           对象存储键（业务 key，会由引擎补全 basePath）
     * @param expireSeconds 策略有效期（秒）
     * @return 直传策略
     */
    default DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        return issueDirectUploadPolicy(DirectUploadRequest.builder().key(key).expireSeconds(expireSeconds).build());
    }

    /**
     * 生成携带客户端期望大小、MIME、校验值及用户元数据的直传策略。
     * <p>
     * 请求中的大小、MIME 与校验值均是客户端声明的预期值；上传完成后，调用方必须使用
     * {@link #statObject(String)} 返回的实际对象元数据进行复核。
     *
     * @param request 直传请求
     * @return 直传策略
     */
    default DirectUploadPolicy issueDirectUploadPolicy(@Nonnull DirectUploadRequest request) {
        int safeExpireSeconds = Math.max(request.getExpireSeconds(), 60);
        return DirectUploadPolicy.builder().success(false)
                .errorMessage("当前存储引擎暂不支持签发直传策略，请使用服务端上传。")
                .method("PUT").uploadUrl(request.getKey()).headers(Map.of()).formFields(Map.of())
                .bucketName("").key(request.getKey()).expireAt(OffsetDateTime.now().plusSeconds(safeExpireSeconds))
                .fallback(true).build();
    }

    /**
     * 生成客户端直读对象存储的统一策略（预签名下载 URL 或可用兜底链接）。
     *
     * @param key           对象存储键（业务 key，会由引擎补全 basePath）
     * @param expireSeconds 策略有效期（秒）
     * @return 直读策略
     */
    default DirectDownloadPolicy issueDirectDownloadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpireSeconds = Math.max(expireSeconds, 60);
        return DirectDownloadPolicy.builder().success(false)
                .errorMessage("当前存储引擎暂不支持签发直读策略，请使用服务端下载。")
                .downloadUrl("").bucketName("").key(key)
                .expireAt(OffsetDateTime.now().plusSeconds(safeExpireSeconds)).fallback(true).build();
    }

    /**
     * 判断指定资源在OS上是否存在的方法
     *
     * @param key 文件在OS上的绝对路径
     * @return 返回判断结果
     */
    boolean existsObject(@Nonnull String key);

    /**
     * 探测对象及其存储元数据。
     * <p>
     * 默认实现保持既有引擎兼容性，并将探测异常转换为 {@code success=false}；已升级的引擎应覆盖本方法，
     * 返回底层存储的完整元数据。
     *
     * @param key 对象存储键
     * @return 对象探测结果
     */
    default ObjectStatResponse statObject(@Nonnull String key) {
        try {
            return ObjectStatResponse.builder().success(true).exists(existsObject(key)).key(key).build();
        } catch (Exception e) {
            return ObjectStatResponse.builder().success(false).exists(false).key(key)
                    .errorCode(e.getClass().getSimpleName()).errorMessage(e.getMessage()).build();
        }
    }

    /**
     * 以受控流读取对象内容，适用于扫描或校验等大文件处理场景。
     * <p>
     * 默认实现通过受控临时文件适配仍只提供服务端下载 API 的旧引擎，不会把对象读入内存。原生支持流式读取的
     * 引擎应覆盖本方法，避免一次临时落盘。
     *
     * @param key      对象存储键
     * @param consumer 对象输入流消费者
     */
    default void readObject(@Nonnull String key, @Nonnull ObjectStreamConsumer consumer) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("storage-object-", ".tmp");
            // 旧实现应覆盖本方法；兼容路径通过已废弃聚合接口下载到受控临时文件。
            DownloadResponse<byte[]> response = ((ServerStorageEngine) this).getObject(key, temporaryFile.toFile(), false);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("读取对象失败: " + (response == null ? "存储引擎未返回下载结果。" : response.getErrorMessage()));
            }
            try (InputStream inputStream = Files.newInputStream(temporaryFile)) { consumer.accept(inputStream); }
        } catch (IOException e) {
            throw new UncheckedIOException("读取对象流失败: " + key, e);
        } finally {
            if (temporaryFile != null) {
                try { Files.deleteIfExists(temporaryFile); }
                catch (IOException e) { throw new UncheckedIOException("清理对象临时文件失败: " + temporaryFile, e); }
            }
        }
    }
}
