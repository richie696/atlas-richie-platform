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
package cn.richie696.component.storage.core;

import cn.richie696.component.storage.bean.DownloadResponse;
import cn.richie696.component.storage.bean.UploadResponse;
import cn.richie696.component.storage.bean.image.ImageOptions;
import jakarta.annotation.Nonnull;
import tools.jackson.core.type.TypeReference;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * 服务端文件存储服务接口。
 * <p>
 * 用于由应用服务器代为上传、下载或序列化对象；客户端直传、直读及上传确认能力请使用
 * {@link DirectStorageEngine}。
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 16:56:45
 */
public interface ServerStorageEngine {

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key        文件在OS上的绝对路径
     * @param collection 文件内容
     * @return 返回推送结果
     */
    UploadResponse putData(@Nonnull String key, @Nonnull Map<?, ?> collection);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key        文件在OS上的绝对路径
     * @param collection 文件内容
     * @return 返回推送结果
     */
    UploadResponse putData(@Nonnull String key, @Nonnull Collection<?> collection);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key    文件在OS上的绝对路径
     * @param object 文件内容
     * @return 返回推送结果
     */
    UploadResponse putData(@Nonnull String key, @Nonnull Object object);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key  文件在OS上的绝对路径
     * @param file 文件内容
     * @return 返回推送结果
     */
    UploadResponse putObject(@Nonnull String key, @Nonnull File file);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key         文件在OS上的绝对路径
     * @param inputStream 文件的输入流
     * @return 返回推送结果
     */
    UploadResponse putObject(@Nonnull String key, @Nonnull InputStream inputStream);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key     文件在OS上的绝对路径
     * @param file    文件内容
     * @param options 图片处理选项
     * @return 返回推送结果
     */
    UploadResponse putImage(@Nonnull String key, @Nonnull File file, ImageOptions options);

    /**
     * 推送文件到文件服务器的方法
     *
     * @param key         文件在OS上的绝对路径
     * @param inputStream 文件的输入流
     * @param options     图片处理选项
     * @return 返回推送结果
     */
    UploadResponse putImage(@Nonnull String key, @Nonnull InputStream inputStream, ImageOptions options);

    /**
     * 获取文件服务器上的Json文件内容并转换为指定类型对象的方法
     *
     * @param key           文件在OS上的绝对路径
     * @param typeReference 内省对象
     * @param <T>           Json文件内容对应的Java类型
     * @return 返回文件内容
     */
    <T> DownloadResponse<T> getData(@Nonnull String key, @Nonnull TypeReference<T> typeReference);

    /**
     * 下载文件到本地服务器的方法
     * <p style="color:green">（注：本方法不会将下载的文件转为字节数组返回）
     *
     * @param key        文件在OS上的绝对路径
     * @param targetPath 文件的本地路径
     * @param returnData 是否返回文件内容<p style="color: red">（如果文件太大可能导致JVM对外内存溢出）
     * @return 返回推送结果
     */
    DownloadResponse<byte[]> getObject(@Nonnull String key, @Nonnull File targetPath, boolean returnData);

    /**
     * 下载文件到本地的方法（支持断点续传）
     *
     * @param key        文件在OS上的绝对路径
     * @param targetPath 用于保存文件的本地路径
     * @param returnData 是否返回文件内容<p style="color: red">（如果文件太大可能导致JVM对外内存溢出）
     * @return 返回下载结果
     */
    DownloadResponse<byte[]> getResumableObject(@Nonnull String key, @Nonnull String targetPath, boolean returnData);
}
