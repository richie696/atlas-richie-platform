package com.richie.component.storage.service.impl;

import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.service.StorageDemoService;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * 文件存储演示服务实现
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageDemoServiceImpl implements StorageDemoService {

    @Qualifier("localStorageEngine")
    private final StorageEngine storageEngine;

    @Override
    public UploadResponse uploadMapData(String key, Map<String, Object> data) {
        log.info("开始上传Map数据, key: {}", key);
        try {
            return storageEngine.putData(key, data);
        } catch (Exception e) {
            log.error("上传Map数据失败", e);
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public UploadResponse uploadCollectionData(String key, Collection<Object> data) {
        log.info("开始上传Collection数据, key: {}", key);
        try {
            return storageEngine.putData(key, data);
        } catch (Exception e) {
            log.error("上传Collection数据失败", e);
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public UploadResponse uploadObjectData(String key, Object data) {
        log.info("开始上传对象数据, key: {}", key);
        try {
            return storageEngine.putData(key, data);
        } catch (Exception e) {
            log.error("上传对象数据失败", e);
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public UploadResponse uploadFile(String key, MultipartFile file) {
        log.info("开始上传文件, key: {}, filename: {}", key, file.getOriginalFilename());
        try {
            return storageEngine.putObject(key, file.getInputStream());
        } catch (IOException e) {
            log.error("上传文件失败", e);
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public UploadResponse uploadImage(String key, MultipartFile file, ImageOptions options) {
        log.info("开始上传图片, key: {}, filename: {}", key, file.getOriginalFilename());
        try {
            return storageEngine.putImage(key, file.getInputStream(), options);
        } catch (IOException e) {
            log.error("上传图片失败", e);
            return UploadResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public <T> DownloadResponse<T> downloadData(String key, TypeReference<T> typeReference) {
        log.info("开始下载JSON数据, key: {}", key);
        try {
            return storageEngine.getData(key, typeReference);
        } catch (Exception e) {
            log.error("下载JSON数据失败", e);
            return DownloadResponse.<T>builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public DownloadResponse<byte[]> downloadFile(String key, String targetPath, boolean returnData) {
        log.info("开始下载文件, key: {}, targetPath: {}", key, targetPath);
        try {
            return storageEngine.getObject(key, new File(targetPath), returnData);
        } catch (Exception e) {
            log.error("下载文件失败", e);
            return DownloadResponse.<byte[]>builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public DownloadResponse<byte[]> downloadResumableFile(String key, String targetPath, boolean returnData) {
        log.info("开始断点续传下载文件, key: {}, targetPath: {}", key, targetPath);
        try {
            return storageEngine.getResumableObject(key, targetPath, returnData);
        } catch (Exception e) {
            log.error("断点续传下载文件失败", e);
            return DownloadResponse.<byte[]>builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public boolean checkFileExists(String key) {
        log.info("检查文件是否存在, key: {}", key);
        try {
            return storageEngine.existsObject(key);
        } catch (Exception e) {
            log.error("检查文件失败", e);
            return false;
        }
    }

}

