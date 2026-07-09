/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.core.support;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.core.impl.AbstractObjectStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 集测用内存/临时目录存储引擎桩。
 */
public final class InMemoryStorageEngineSupport {

    private InMemoryStorageEngineSupport() {
    }

    public static InMemoryStorageEngine create(Path root) {
        StorageProperties props = new StorageProperties();
        ObjectConfig object = new ObjectConfig();
        object.setBucketName("mem");
        object.setEndpoint("mem.local");
        object.setBasePath(root.toString().replace('\\', '/'));
        props.setObject(object);
        return new InMemoryStorageEngine(props, root);
    }

    public static final class InMemoryStorageEngine extends AbstractObjectStorageEngine<Path> {
        private final Path root;

        InMemoryStorageEngine(StorageProperties properties, Path root) {
            super(properties, new StorageTypeConverter() {
                @Override
                public String convertToEngineType(StorageTypeEnum storageType) {
                    return "STANDARD";
                }

                @Override
                public StorageEngineEnum getSupportedEngine() {
                    return StorageEngineEnum.AWS_S3;
                }
            });
            this.root = root;
        }

        @Override
        public UploadResponse putObject(@NonNull String key, @NonNull InputStream inputStream) {
            try {
                Path target = root.resolve(key);
                Files.createDirectories(target.getParent());
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                return UploadResponse.builder().success(true).key(getRealPath(key)).build();
            } catch (IOException e) {
                return UploadResponse.builder().success(false).errorMessage(e.getMessage()).build();
            }
        }

        @Override
        public UploadResponse putObject(@NonNull String key, @NonNull File file) {
            try {
                return putObject(key, new java.io.FileInputStream(file));
            } catch (java.io.FileNotFoundException e) {
                return UploadResponse.builder().success(false).errorMessage(e.getMessage()).build();
            }
        }

        @Override
        public UploadResponse putImage(@NonNull String key, @NonNull File file, ImageOptions options) {
            return putObject(key, file);
        }

        @Override
        public UploadResponse putImage(@NonNull String key, @NonNull InputStream inputStream, ImageOptions options) {
            return putObject(key, inputStream);
        }

        @Override
        public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(@NonNull String key,
                                                                                 @NonNull TypeReference<T> typeReference) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(@NonNull String key, @NonNull File targetPath,
                                                                                    boolean returnData) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(@NonNull String key,
                                                                                             @NonNull String targetPath, boolean returnData) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public boolean existsObject(@NonNull String key) {
            return Files.exists(root.resolve(key));
        }
    }
}
