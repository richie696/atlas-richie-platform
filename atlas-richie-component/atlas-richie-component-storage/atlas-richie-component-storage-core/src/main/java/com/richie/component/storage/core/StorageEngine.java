/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.core;

import com.richie.component.storage.bean.DirectDownloadPolicy;
import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import jakarta.annotation.Nonnull;
import tools.jackson.core.type.TypeReference;

import java.io.File;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 文件存储服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 16:56:45
 */
public interface StorageEngine {

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param collection 文件内容
     * @return 返回推送结果
     */
    UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param collection 文件内容
     * @return 返回推送结果
     */
    UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param object 文件内容
     * @return 返回推送结果
     */
    UploadResponse putData(@Nonnull String key, @Nonnull Object object);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param file 文件内容
     * @return 返回推送结果
     */
    UploadResponse putObject(@Nonnull String key, @Nonnull File file);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param inputStream 文件的输入流
     * @return 返回推送结果
     */
    UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param file 文件内容
     * @param options 图片处理选项
     * @return 返回推送结果
     */
    UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param inputStream 文件的输入流
     * @param options 图片处理选项
     * @return 返回推送结果
     */
    UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream,ImageOptions options);

    /**
     * 获取文件服务器上的Json文件内容并转换为指定类型对象的方法
     *
     * @param key 文件在OS上的绝对路径
     * @param typeReference 内省对象
     * @return 返回文件内容
     * @param <T> Json文件内容对应的Java类型
     */
    <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeReference);

    /**
     * 下载文件到本地服务器的方法
     * <p style="color:green">（注：本方法不会将下载的文件转为字节数组返回）
     *
     * @param key 文件在OS上的绝对路径
     * @param targetPath 文件的本地路径
     * @param returnData 是否返回文件内容<p style="color: red">（如果文件太大可能导致JVM对外内存溢出）
     * @return 返回推送结果
     */
    DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData);

    /**
     * 下载文件到本地的方法（支持断点续传）
     *
     * @param key 文件在OS上的绝对路径
     * @param targetPath 用于保存文件的本地路径
     * @param returnData 是否返回文件内容<p style="color: red">（如果文件太大可能导致JVM对外内存溢出）
     * @return 返回下载结果
     */
    DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData);

    /**
     * 判断指定资源在OS上是否存在的方法
     *
     * @param key 文件在OS上的绝对路径
     * @return 返回判断结果
     */
    boolean existsObject(@Nonnull String key);

    /**
     * 生成客户端直传对象存储的统一策略（预签名 URL 或可用兜底链接）。
     *
     * @param key 对象存储键（业务 key，会由引擎补全 basePath）
     * @param expireSeconds 策略有效期（秒）
     * @return 直传策略
     */
    default DirectUploadPolicy issueDirectUploadPolicy(@Nonnull String key, int expireSeconds) {
        int safeExpireSeconds = Math.max(expireSeconds, 60);
        return DirectUploadPolicy.builder()
                .success(false)
                .errorMessage("当前存储引擎暂不支持签发直传策略，请使用服务端上传。")
                .method("PUT")
                .uploadUrl(key)
                .headers(Map.of())
                .formFields(Map.of())
                .bucketName("")
                .key(key)
                .expireAt(OffsetDateTime.now().plusSeconds(safeExpireSeconds))
                .fallback(true)
                .build();
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
        return DirectDownloadPolicy.builder()
                .success(false)
                .errorMessage("当前存储引擎暂不支持签发直读策略，请使用服务端下载。")
                .downloadUrl("")
                .bucketName("")
                .key(key)
                .expireAt(OffsetDateTime.now().plusSeconds(safeExpireSeconds))
                .fallback(true)
                .build();
    }

}
