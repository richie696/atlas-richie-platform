package com.richie.component.storage.controller;

import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.service.StorageDemoService;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;

/**
 * 文件存储演示控制器
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-30
 */
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageDemoController {

    private final StorageDemoService storageDemoService;

    /**
     * 上传Map数据
     *
     * @param key  文件键
     * @param data 数据内容
     * @return 上传响应
     */
    @PostMapping("/data/map")
    public UploadResponse uploadMapData(@RequestParam String key, @RequestBody Map<String, Object> data) {
        return storageDemoService.uploadMapData(key, data);
    }

    /**
     * 上传Collection数据
     *
     * @param key  文件键
     * @param data 数据内容
     * @return 上传响应
     */
    @PostMapping("/data/collection")
    public UploadResponse uploadCollectionData(@RequestParam String key, @RequestBody Collection<Object> data) {
        return storageDemoService.uploadCollectionData(key, data);
    }

    /**
     * 上传对象数据
     *
     * @param key  文件键
     * @param data 对象数据
     * @return 上传响应
     */
    @PostMapping("/data/object")
    public UploadResponse uploadObjectData(@RequestParam String key, @RequestBody Object data) {
        return storageDemoService.uploadObjectData(key, data);
    }

    /**
     * 上传文件
     *
     * @param key  文件键
     * @param file 文件
     * @return 上传响应
     */
    @PostMapping("/file/upload")
    public UploadResponse uploadFile(@RequestParam String key, @RequestParam("file") MultipartFile file) {
        return storageDemoService.uploadFile(key, file);
    }

    /**
     * 上传图片
     *
     * @param key     文件键
     * @param file    图片文件
     * @param scale   缩放比例
     * @param quality 图片质量
     * @return 上传响应
     */
    @PostMapping("/image/upload")
    public UploadResponse uploadImage(@RequestParam String key,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) Integer scale,
                                      @RequestParam(required = false) Integer quality) {
        ImageOptions options = new ImageOptions();
        options.setScale(scale);
        options.setQuality(quality);
        return storageDemoService.uploadImage(key, file, options);
    }

    /**
     * 下载JSON数据
     *
     * @param key 文件键
     * @return 下载响应
     */
    @GetMapping("/data/download")
    public DownloadResponse<Map<String, Object>> downloadData(@RequestParam String key) {
        return storageDemoService.downloadData(key, new TypeReference<>() {
        });
    }

    /**
     * 下载文件到指定路径
     *
     * @param key        文件键
     * @param targetPath 目标路径
     * @param returnData 是否返回数据
     * @return 下载响应
     */
    @GetMapping("/file/download")
    public DownloadResponse<byte[]> downloadFile(@RequestParam String key,
                                                 @RequestParam String targetPath,
                                                 @RequestParam(defaultValue = "false") boolean returnData) {
        return storageDemoService.downloadFile(key, targetPath, returnData);
    }

    /**
     * 断点续传下载文件
     *
     * @param key        文件键
     * @param targetPath 目标路径
     * @param returnData 是否返回数据
     * @return 下载响应
     */
    @GetMapping("/file/download/resumable")
    public DownloadResponse<byte[]> downloadResumableFile(@RequestParam String key,
                                                          @RequestParam String targetPath,
                                                          @RequestParam(defaultValue = "false") boolean returnData) {
        return storageDemoService.downloadResumableFile(key, targetPath, returnData);
    }

    /**
     * 检查文件是否存在
     *
     * @param key 文件键
     * @return 是否存在
     */
    @GetMapping("/file/exists")
    public boolean checkFileExists(@RequestParam String key) {
        return storageDemoService.checkFileExists(key);
    }

}

