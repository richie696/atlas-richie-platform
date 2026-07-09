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

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.enums.StorageEngineEnum;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 存储引擎 Provider SPI 单元测试
 * <p>
 * 覆盖 default 方法的 no-op 行为、SPI 契约（接口可被实现）。
 */
class StorageEngineProviderTest {

    /**
     * 仅实现 supportedEngineType + create 的最小 Provider，
     * 用于测试 afterPropertiesSet/destroy/validate 的 default 行为。
     */
    static class MinimalProvider implements StorageEngineProvider {
        @Override
        public StorageEngineEnum supportedEngineType() {
            return StorageEngineEnum.LOCAL;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            return new MinimalStorageEngine();
        }
    }

    /**
     * 完整实现所有方法的 Provider，用于测试 override 行为。
     */
    static class FullProvider implements StorageEngineProvider {
        final AtomicReference<StorageEngine> afterPropertiesSetCalled = new AtomicReference<>();
        final AtomicReference<StorageEngine> destroyCalled = new AtomicReference<>();
        boolean validateCalled = false;

        @Override
        public StorageEngineEnum supportedEngineType() {
            return StorageEngineEnum.MINIO;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            return new MinimalStorageEngine();
        }

        @Override
        public void afterPropertiesSet(StorageEngine engine) {
            afterPropertiesSetCalled.set(engine);
        }

        @Override
        public void destroy(StorageEngine engine) {
            destroyCalled.set(engine);
        }

        @Override
        public void validate(StorageProperties properties) {
            validateCalled = true;
        }
    }

    @Test
    void minimalProvider_defaultAfterPropertiesSet_shouldBeNoOp() {
        StorageEngineProvider provider = new MinimalProvider();
        assertThatCode(() -> provider.afterPropertiesSet(new MinimalStorageEngine()))
                .doesNotThrowAnyException();
    }

    @Test
    void minimalProvider_defaultDestroy_shouldBeNoOp() {
        StorageEngineProvider provider = new MinimalProvider();
        assertThatCode(() -> provider.destroy(new MinimalStorageEngine()))
                .doesNotThrowAnyException();
    }

    @Test
    void minimalProvider_defaultValidate_shouldBeNoOp() {
        StorageEngineProvider provider = new MinimalProvider();
        assertThatCode(() -> provider.validate(new StorageProperties()))
                .doesNotThrowAnyException();
    }

    @Test
    void fullProvider_supportedEngineType_shouldReturnDeclaredType() {
        FullProvider provider = new FullProvider();
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.MINIO);
    }

    @Test
    void fullProvider_afterPropertiesSet_shouldReceiveEngine() {
        FullProvider provider = new FullProvider();
        StorageEngine engine = new MinimalStorageEngine();
        provider.afterPropertiesSet(engine);
        assertThat(provider.afterPropertiesSetCalled.get()).isSameAs(engine);
    }

    @Test
    void fullProvider_destroy_shouldReceiveEngine() {
        FullProvider provider = new FullProvider();
        StorageEngine engine = new MinimalStorageEngine();
        provider.destroy(engine);
        assertThat(provider.destroyCalled.get()).isSameAs(engine);
    }

    @Test
    void fullProvider_validate_shouldBeInvokable() {
        FullProvider provider = new FullProvider();
        provider.validate(new StorageProperties());
        assertThat(provider.validateCalled).isTrue();
    }

    @Test
    void spiInterface_shouldBeImplementable() {
        // 编译期类型检查：StorageEngineProvider 是 public interface
        StorageEngineProvider provider = new MinimalProvider();
        assertThat(provider).isInstanceOf(StorageEngineProvider.class);
    }

    /**
     * 最小的 StorageEngine 实现，覆盖接口中所有抽象方法。
     * 仅用于 Provider 的契约测试，不进行实际存储操作。
     */
    static class MinimalStorageEngine implements StorageEngine {
        @Override
        public com.richie.component.storage.bean.UploadResponse putData(String key, java.util.Map<?, ?> collection) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putData(String key, java.util.Collection<?> collection) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putData(String key, Object object) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.File file) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.InputStream inputStream) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.File file,
                                                                          com.richie.component.storage.bean.image.ImageOptions options) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.InputStream inputStream,
                                                                          com.richie.component.storage.bean.image.ImageOptions options) {
            return null;
        }

        @Override
        public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(String key,
                                                                                 tools.jackson.core.type.TypeReference<T> typeReference) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(String key, java.io.File targetPath,
                                                                                   boolean returnData) {
            return null;
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(String key, String targetPath,
                                                                                              boolean returnData) {
            return null;
        }

        @Override
        public boolean existsObject(String key) {
            return false;
        }
    }
}
