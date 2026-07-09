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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collection;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StorageEngineProxyFactoryBean 单元测试
 * <p>
 * 覆盖：FactoryBean 契约、代理对象创建、setDelegate 行为、未初始化时调用方法抛 IllegalStateException、
 * toString/hashCode/equals 行为、default 方法透传、InvocationTargetException 解包。
 */
class StorageEngineProxyFactoryBeanTest {

    @Test
    void getObject_shouldReturnNonNullProxy() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        StorageEngine proxy = factoryBean.getObject();
        assertThat(proxy).isNotNull();
    }

    @Test
    void getObjectType_shouldReturnStorageEngine() {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        assertThat(factoryBean.getObjectType()).isEqualTo(StorageEngine.class);
    }

    @Test
    void shouldImplementFactoryBeanInterface() {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        assertThat(factoryBean).isInstanceOf(FactoryBean.class);
    }

    @Test
    void isSingleton_shouldBeTrueByDefault() {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        assertThat(factoryBean.isSingleton()).isTrue();
    }

    @Test
    void setDelegate_shouldUpdateDelegateReference() {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        RecordingEngine engine = new RecordingEngine();
        factoryBean.setDelegate(engine);
        assertThat(factoryBean.getDelegate()).isSameAs(engine);
        assertThat(factoryBean.isAvailable()).isTrue();
    }

    @Test
    void setDelegate_shouldOverwritePreviousDelegate() {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        RecordingEngine first = new RecordingEngine();
        RecordingEngine second = new RecordingEngine();
        factoryBean.setDelegate(first);
        factoryBean.setDelegate(second);
        assertThat(factoryBean.getDelegate()).isSameAs(second);
    }

    @Test
    void setDelegate_toNull_shouldMarkUnavailable() {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        factoryBean.setDelegate(new RecordingEngine());
        assertThat(factoryBean.isAvailable()).isTrue();
        factoryBean.setDelegate(null);
        assertThat(factoryBean.isAvailable()).isFalse();
        assertThat(factoryBean.getDelegate()).isNull();
    }

    @Test
    void isAvailable_initialStateShouldBeFalse() {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        assertThat(factoryBean.isAvailable()).isFalse();
        assertThat(factoryBean.getDelegate()).isNull();
    }

    @Test
    void proxy_toString_shouldIncludeDelegateClassName() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        StorageEngine proxy = factoryBean.getObject();
        assertThat(proxy.toString()).isEqualTo("StorageEngineProxy[delegate=null]");

        factoryBean.setDelegate(new RecordingEngine());
        StorageEngine proxy2 = factoryBean.getObject();
        assertThat(proxy2.toString()).isEqualTo("StorageEngineProxy[delegate=RecordingEngine]");
    }

    @Test
    void proxy_hashCode_shouldBeIdentityHashCode() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        StorageEngine proxy = factoryBean.getObject();
        assertThat(proxy.hashCode()).isEqualTo(System.identityHashCode(proxy));
    }

    @Test
    void proxy_equals_shouldUseIdentity() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        StorageEngine proxy = factoryBean.getObject();
        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.equals(new Object())).isFalse();
        assertThat(proxy.equals(null)).isFalse();
    }

    @Test
    void proxy_uninitializedCall_shouldThrowIllegalStateException() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        StorageEngine proxy = factoryBean.getObject();
        assertThatThrownBy(() -> proxy.existsObject("key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("存储引擎未初始化");
    }

    @Test
    void proxy_delegatedCall_shouldInvokeDelegate() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        RecordingEngine delegate = new RecordingEngine();
        factoryBean.setDelegate(delegate);
        StorageEngine proxy = factoryBean.getObject();

        boolean result = proxy.existsObject("test-key");
        assertThat(result).isTrue();
        assertThat(delegate.lastExistsKey).isEqualTo("test-key");
    }

    @Test
    void proxy_defaultMethod_shouldUseInvokeDefault() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        StorageEngine proxy = factoryBean.getObject();
        DirectUploadPolicy policy = proxy.issueDirectUploadPolicy("k", 10);
        assertThat(policy).isNotNull();
        assertThat(policy.isSuccess()).isFalse();
    }

    @Test
    void proxy_defaultDownloadPolicy_shouldUseInvokeDefault() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        StorageEngine proxy = factoryBean.getObject();
        DirectDownloadPolicy policy = proxy.issueDirectDownloadPolicy("k", 10);
        assertThat(policy).isNotNull();
        assertThat(policy.isSuccess()).isFalse();
    }

    @Test
    void proxy_invocationTargetException_shouldUnwrapCause() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        factoryBean.setDelegate(new ThrowingEngine(new IllegalArgumentException("original cause")));
        StorageEngine proxy = factoryBean.getObject();
        assertThatThrownBy(() -> proxy.existsObject("k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("original cause");
    }

    @Test
    void proxy_hotSwitch_shouldUseNewDelegateOnNextCall() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        RecordingEngine first = new RecordingEngine();
        RecordingEngine second = new RecordingEngine();
        factoryBean.setDelegate(first);
        StorageEngine proxy = factoryBean.getObject();

        proxy.existsObject("a");
        assertThat(first.existsCount).isEqualTo(1);
        assertThat(second.existsCount).isEqualTo(0);

        factoryBean.setDelegate(second);
        proxy.existsObject("b");
        assertThat(first.existsCount).isEqualTo(1);
        assertThat(second.existsCount).isEqualTo(1);
    }

    @Test
    void proxy_getObject_shouldReturnSameTypeEachCall() throws Exception {
        StorageEngineProxyFactoryBean factoryBean = new StorageEngineProxyFactoryBean();
        // FactoryBean 接口契约：getObject() 类型应与 getObjectType() 一致
        assertThat(factoryBean.getObject()).isInstanceOf(StorageEngine.class);
        assertThat(factoryBean.getObjectType().isInterface()).isTrue();
    }

    /**
     * 用于验证委托调用是否被正确转发到 delegate。
     */
    static class RecordingEngine implements StorageEngine {
        String lastExistsKey;
        int existsCount;

        @Override
        public UploadResponse putData(String key, Map<?, ?> collection) {
            return null;
        }

        @Override
        public UploadResponse putData(String key, Collection<?> collection) {
            return null;
        }

        @Override
        public UploadResponse putData(String key, Object object) {
            return null;
        }

        @Override
        public UploadResponse putObject(String key, File file) {
            return null;
        }

        @Override
        public UploadResponse putObject(String key, InputStream inputStream) {
            return null;
        }

        @Override
        public UploadResponse putImage(String key, File file, ImageOptions options) {
            return null;
        }

        @Override
        public UploadResponse putImage(String key, InputStream inputStream, ImageOptions options) {
            return null;
        }

        @Override
        public <T> DownloadResponse<T> getData(String key, TypeReference<T> typeReference) {
            return null;
        }

        @Override
        public DownloadResponse<byte[]> getObject(String key, File targetPath, boolean returnData) {
            return null;
        }

        @Override
        public DownloadResponse<byte[]> getResumableObject(String key, String targetPath, boolean returnData) {
            return null;
        }

        @Override
        public boolean existsObject(String key) {
            this.lastExistsKey = key;
            this.existsCount++;
            return true;
        }
    }

    /**
     * 用于验证 InvocationTargetException 解包行为。
     */
    static class ThrowingEngine implements StorageEngine {
        private final RuntimeException toThrow;

        ThrowingEngine(RuntimeException toThrow) {
            this.toThrow = toThrow;
        }

        @Override
        public UploadResponse putData(String key, Map<?, ?> collection) {
            throw toThrow;
        }

        @Override
        public UploadResponse putData(String key, Collection<?> collection) {
            throw toThrow;
        }

        @Override
        public UploadResponse putData(String key, Object object) {
            throw toThrow;
        }

        @Override
        public UploadResponse putObject(String key, File file) {
            throw toThrow;
        }

        @Override
        public UploadResponse putObject(String key, InputStream inputStream) {
            throw toThrow;
        }

        @Override
        public UploadResponse putImage(String key, File file, ImageOptions options) {
            throw toThrow;
        }

        @Override
        public UploadResponse putImage(String key, InputStream inputStream, ImageOptions options) {
            throw toThrow;
        }

        @Override
        public <T> DownloadResponse<T> getData(String key, TypeReference<T> typeReference) {
            throw toThrow;
        }

        @Override
        public DownloadResponse<byte[]> getObject(String key, File targetPath, boolean returnData) {
            throw toThrow;
        }

        @Override
        public DownloadResponse<byte[]> getResumableObject(String key, String targetPath, boolean returnData) {
            throw toThrow;
        }

        @Override
        public boolean existsObject(String key) {
            throw toThrow;
        }
    }
}
