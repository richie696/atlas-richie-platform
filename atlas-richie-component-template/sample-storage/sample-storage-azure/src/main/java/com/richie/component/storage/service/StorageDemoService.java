package com.richie.component.storage.service;

import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import tools.jackson.core.type.TypeReference;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;

/**
 * 文件存储演示服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-30
 */
public interface StorageDemoService {

    /**
     * 上传Map数据
     *
     * @param key  文件键
     * @param data 数据内容
     * @return 上传响应
     */
    UploadResponse uploadMapData(String key, Map<String, Object> data);

    /**
     * 上传Collection数据
     *
     * @param key  文件键
     * @param data 数据内容
     * @return 上传响应
     */
    UploadResponse uploadCollectionData(String key, Collection<Object> data);

    /**
     * 上传对象数据
     *
     * @param key  文件键
     * @param data 对象数据
     * @return 上传响应
     */
    UploadResponse uploadObjectData(String key, Object data);

    /**
     * 上传文件
     *
     * @param key  文件键
     * @param file 文件
     * @return 上传响应
     */
    UploadResponse uploadFile(String key, MultipartFile file);

    /**
     * 上传图片
     *
     * @param key     文件键
     * @param file    图片文件
     * @param options 图片处理选项
     * @return 上传响应
     */
    UploadResponse uploadImage(String key, MultipartFile file, ImageOptions options);

    /**
     * 下载JSON数据
     *
     * @param key           文件键
     * @param typeReference 类型引用
     * @param <T>           数据类型
     * @return 下载响应
     */
    <T> DownloadResponse<T> downloadData(String key, TypeReference<T> typeReference);

    /**
     * 下载文件到指定路径
     *
     * @param key        文件键
     * @param targetPath 目标路径
     * @param returnData 是否返回数据
     * @return 下载响应
     */
    DownloadResponse<byte[]> downloadFile(String key, String targetPath, boolean returnData);

    /**
     * 断点续传下载文件
     *
     * @param key        文件键
     * @param targetPath 目标路径
     * @param returnData 是否返回数据
     * @return 下载响应
     */
    DownloadResponse<byte[]> downloadResumableFile(String key, String targetPath, boolean returnData);

    /**
     * 检查文件是否存在
     *
     * @param key 文件键
     * @return 是否存在
     */
    boolean checkFileExists(String key);

}

