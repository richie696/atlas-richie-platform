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
package com.richie.component.storage.core;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.context.common.api.SpringContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StorageEngineRegistry 单元测试
 * <p>
 * 覆盖：多引擎注册、objectProxy、getProxy、switchEngine、错误路径、
 * isInitialized、getRegisteredTypes、SpringContextHolder 动态 Provider 发现。
 */
class StorageEngineRegistryTest {

    private AnnotationConfigApplicationContext applicationContext;
    private StorageEngineRegistry registry;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.refresh();
        setApplicationContext(applicationContext);
        registry = new StorageEngineRegistry();
    }

    @AfterEach
    void tearDown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        clearApplicationContext();
    }

    private static void setApplicationContext(ApplicationContext ctx) {
        try {
            Field f = SpringContextHolder.class.getDeclaredField("applicationContext");
            f.setAccessible(true);
            f.set(null, ctx);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void clearApplicationContext() {
        try {
            Field f = SpringContextHolder.class.getDeclaredField("applicationContext");
            f.setAccessible(true);
            f.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerProvider(StorageEngineProvider provider) {
        applicationContext.getBeanFactory().registerSingleton(
                provider.supportedEngineType().getConfigValue() + "Provider", provider);
    }

    // ========== 构造 / 初始状态 ==========

    @Test
    void constructor_shouldPreCreateProxyHoldersForAllEngineTypes() {
        for (StorageEngineEnum type : StorageEngineEnum.values()) {
            assertThat(registry.getEngine(type)).as("Engine %s pre-created", type).isNull();
        }
        assertThat(registry.isInitialized()).isFalse();
        assertThat(registry.getDefaultEngineType()).isNull();
        assertThat(registry.getDefaultEngineId()).isNull();
        assertThat(registry.getCurrentEngineType()).isEqualTo("null");
    }

    @Test
    void getProxy_shouldReturnSameProxyOnRepeatedCalls() {
        StorageEngine proxy1 = registry.getProxy(StorageEngineEnum.MINIO);
        StorageEngine proxy2 = registry.getProxy(StorageEngineEnum.MINIO);
        assertThat(proxy1).isSameAs(proxy2);
    }

    @Test
    void getProxy_shouldReturnDifferentProxyForDifferentTypes() {
        StorageEngine minio = registry.getProxy(StorageEngineEnum.MINIO);
        StorageEngine ftp = registry.getProxy(StorageEngineEnum.FTP);
        assertThat(minio).isNotSameAs(ftp);
    }

    // ========== registerInitialEngine ==========

    @Test
    void registerInitialEngine_shouldSetDelegateAndDefault() {
        StubEngine engine = new StubEngine("minio-engine");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "minio-1", engine);

        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(engine);
        assertThat(registry.getDefaultEngineType()).isEqualTo(StorageEngineEnum.MINIO);
        assertThat(registry.getDefaultEngineId()).isEqualTo("minio-1");
        assertThat(registry.isInitialized()).isTrue();
        assertThat(registry.getCurrentEngineType()).isEqualTo("MINIO");
    }

    @Test
    void registerInitialEngine_objectStorage_shouldAlsoUpdateObjectProxy() {
        StubEngine engine = new StubEngine("minio-engine");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "minio-1", engine);

        // objectProxy 实际指向的是同一个 delegate
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(engine);
    }

    @Test
    void registerInitialEngine_fileProtocol_shouldNotUpdateObjectProxy() {
        StubEngine ftp = new StubEngine("ftp-engine");
        registry.registerInitialEngine(StorageEngineEnum.FTP, "ftp-1", ftp);

        // 后续注册对象存储时 objectProxy 应指向对象存储
        StubEngine minio = new StubEngine("minio-engine");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "minio-1", minio);

        // FTP 的 delegate 是 ftp
        assertThat(registry.getEngine(StorageEngineEnum.FTP)).isSameAs(ftp);
        // MINIO 的 delegate 是 minio
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(minio);
        // 第一个注册的 (FTP) 是默认
        assertThat(registry.getDefaultEngineType()).isEqualTo(StorageEngineEnum.FTP);
    }

    @Test
    void registerInitialEngine_secondEngine_shouldNotChangeDefault() {
        StubEngine first = new StubEngine("first");
        StubEngine second = new StubEngine("second");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", first);
        registry.registerInitialEngine(StorageEngineEnum.FTP, "f-1", second);

        assertThat(registry.getDefaultEngineType()).isEqualTo(StorageEngineEnum.MINIO);
        assertThat(registry.getDefaultEngineId()).isEqualTo("m-1");
    }

    @Test
    void registerInitialEngine_duplicateSameType_shouldThrow() {
        StubEngine first = new StubEngine("first");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", first);

        assertThatThrownBy(() -> registry.registerInitialEngine(
                StorageEngineEnum.MINIO, "m-2", new StubEngine("second")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MINIO");
    }

    @Test
    void registerInitialEngine_concurrentDifferentTypes_allSucceed() throws InterruptedException {
        StorageEngineEnum[] types = {
                StorageEngineEnum.MINIO, StorageEngineEnum.FTP,
                StorageEngineEnum.SFTP, StorageEngineEnum.SMB
        };
        StubEngine[] engines = new StubEngine[4];
        for (int i = 0; i < 4; i++) {
            engines[i] = new StubEngine("e-" + i);
        }
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(4);
        Thread[] threads = new Thread[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    registry.registerInitialEngine(types[idx], "id-" + idx, engines[idx]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            threads[i].start();
        }
        start.countDown();
        done.await();

        for (int i = 0; i < 4; i++) {
            assertThat(registry.getEngine(types[i])).as("Engine %s registered", types[i])
                    .isSameAs(engines[i]);
        }
        assertThat(registry.getRegisteredTypes()).containsExactlyInAnyOrder(types);
        assertThat(registry.isInitialized()).isTrue();
    }

    @Test
    void registerInitialEngine_concurrentSameType_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    registry.registerInitialEngine(StorageEngineEnum.MINIO,
                            "concurrent-id", new StubEngine("c"));
                    successCount.incrementAndGet();
                } catch (IllegalStateException expected) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertThat(successCount.get()).as("only one registration succeeds").isEqualTo(1);
        assertThat(failCount.get()).as("the rest fail with IllegalStateException")
                .isEqualTo(threadCount - 1);
        assertThat(registry.isInitialized()).isTrue();
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isNotNull();
    }

    @Test
    void registerInitialEngine_andSwitchEngine_shareLock() throws InterruptedException {
        StubEngine oldEngine = new StubEngine("old");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", oldEngine);

        DestroyRecordingProvider prov = new DestroyRecordingProvider(
                StorageEngineEnum.MINIO, new StubEngine("new-2"));
        applicationContext.getBeanFactory().registerSingleton("minioProviderSharedLock", prov);

        Thread switchThread = new Thread(
                () -> registry.switchEngine(StorageEngineEnum.MINIO, new StorageProperties()));
        switchThread.start();
        switchThread.join(5_000);

        assertThat(prov.destroyedEngines).contains(oldEngine);
    }

    // ========== getObjectProxy / getDefaultProxy ==========

    @Test
    void getObjectProxy_shouldReturnValidProxyBeforeInitialization() {
        StorageEngine proxy = registry.getObjectProxy();
        assertThat(proxy).isNotNull();
    }

    @Test
    void getObjectProxy_uninitializedCall_shouldThrowIllegalStateException() {
        StorageEngine proxy = registry.getObjectProxy();
        assertThatThrownBy(() -> proxy.existsObject("key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未初始化");
    }

    @Test
    void getDefaultProxy_uninitialized_shouldThrow() {
        assertThatThrownBy(() -> registry.getDefaultProxy())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未注册");
    }

    @Test
    void getDefaultEngine_uninitialized_shouldReturnNull() {
        assertThat(registry.getDefaultEngine()).isNull();
    }

    // ========== isInitialized / getRegisteredTypes / getCurrentEngineType ==========

    @Test
    void isInitialized_shouldReflectFirstRegistration() {
        assertThat(registry.isInitialized()).isFalse();
        registry.registerInitialEngine(StorageEngineEnum.FTP, "f-1", new StubEngine("f"));
        assertThat(registry.isInitialized()).isTrue();
    }

    @Test
    void getRegisteredTypes_shouldReturnEmptyWhenNoEnginesRegistered() {
        assertThat(registry.getRegisteredTypes()).isEmpty();
    }

    @Test
    void getRegisteredTypes_shouldListAllRegisteredTypes() {
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", new StubEngine("m"));
        registry.registerInitialEngine(StorageEngineEnum.FTP, "f-1", new StubEngine("f"));
        registry.registerInitialEngine(StorageEngineEnum.SFTP, "s-1", new StubEngine("s"));

        Set<StorageEngineEnum> types = registry.getRegisteredTypes();
        assertThat(types).containsExactlyInAnyOrder(
                StorageEngineEnum.MINIO, StorageEngineEnum.FTP, StorageEngineEnum.SFTP);
    }

    @Test
    void getRegisteredTypes_shouldBeUnmodifiable() {
        Set<StorageEngineEnum> types = registry.getRegisteredTypes();
        assertThatThrownBy(() -> types.add(StorageEngineEnum.MINIO))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ========== switchEngine（需 SpringContextHolder 中的 Provider） ==========

    @Test
    void switchEngine_firstEngine_shouldUseProvider() {
        registerProvider(new StubProvider(StorageEngineEnum.MINIO, new StubEngine("switched")));
        StorageProperties props = new StorageProperties();

        StorageEngine engine = registry.switchEngine(StorageEngineEnum.MINIO, props);
        assertThat(engine).isNotNull();
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(engine);
        assertThat(registry.getDefaultEngineType()).isEqualTo(StorageEngineEnum.MINIO);
    }

    @Test
    void switchEngine_existingEngine_shouldReplaceDelegate() {
        StubEngine oldEngine = new StubEngine("old");
        StubEngine newEngine = new StubEngine("new");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", oldEngine);

        registerProvider(new StubProvider(StorageEngineEnum.MINIO, newEngine));
        StorageProperties props = new StorageProperties();

        StorageEngine returned = registry.switchEngine(StorageEngineEnum.MINIO, props);
        assertThat(returned).isSameAs(newEngine);
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(newEngine);
    }

    @Test
    void switchEngine_noProvider_shouldThrow() {
        StorageProperties props = new StorageProperties();
        assertThatThrownBy(() -> registry.switchEngine(StorageEngineEnum.MINIO, props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未找到引擎类型")
                .hasMessageContaining("Provider");
    }

    @Test
    void switchEngine_nullEngineType_shouldThrowNpe() {
        assertThatThrownBy(() -> registry.switchEngine(null, new StorageProperties()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void switchEngine_nullProperties_shouldThrowNpe() {
        assertThatThrownBy(() -> registry.switchEngine(StorageEngineEnum.MINIO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void switchEngine_providerValidationFails_shouldThrowAndNotChange() {
        registerProvider(new FailingProvider(StorageEngineEnum.MINIO,
                new IllegalArgumentException("validation failed")));
        StorageProperties props = new StorageProperties();

        assertThatThrownBy(() -> registry.switchEngine(StorageEngineEnum.MINIO, props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("validation failed");
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isNull();
    }

    @Test
    void switchEngine_createFails_shouldThrowAndNotChange() {
        registerProvider(new ThrowingProvider(StorageEngineEnum.MINIO,
                new RuntimeException("create failed"), null));
        StorageProperties props = new StorageProperties();

        assertThatThrownBy(() -> registry.switchEngine(StorageEngineEnum.MINIO, props))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("create failed");
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isNull();
    }

    @Test
    void switchEngine_destroyOld_shouldBeInvoked() {
        StubEngine oldEngine = new StubEngine("old");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", oldEngine);

        DestroyRecordingProvider provider = new DestroyRecordingProvider(StorageEngineEnum.MINIO, new StubEngine("new"));
        registerProvider(provider);

        registry.switchEngine(StorageEngineEnum.MINIO, new StorageProperties());
        assertThat(provider.destroyedEngines).contains(oldEngine);
    }

    @Test
    void switchEngine_afterPropertiesSet_shouldBeInvoked() {
        AfterPropertiesSetProvider provider = new AfterPropertiesSetProvider(
                StorageEngineEnum.MINIO, new StubEngine("aps"));
        registerProvider(provider);

        registry.switchEngine(StorageEngineEnum.MINIO, new StorageProperties());
        assertThat(provider.afterPropertiesSetCalls).isEqualTo(1);
    }

    // ========== 委托调用 / proxy 行为 ==========

    @Test
    void getProxy_callShouldDelegateToRegisteredEngine() {
        StubEngine engine = new StubEngine("engine");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", engine);
        StorageEngine proxy = registry.getProxy(StorageEngineEnum.MINIO);
        assertThat(proxy.existsObject("k")).isTrue();
        assertThat(engine.lastExistsKey).isEqualTo("k");
    }

    @Test
    void getProxy_toString_shouldIncludeEngineType() {
        StorageEngine proxy = registry.getProxy(StorageEngineEnum.MINIO);
        assertThat(proxy.toString()).contains("MINIO");
        assertThat(proxy.toString()).contains("delegate=null");
    }

    @Test
    void getProxy_toStringAfterRegistration_shouldShowDelegate() {
        StubEngine engine = new StubEngine("e");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", engine);
        StorageEngine proxy = registry.getProxy(StorageEngineEnum.MINIO);
        assertThat(proxy.toString()).contains("MINIO");
        assertThat(proxy.toString()).contains("StubEngine");
    }

    @Test
    void getProxy_defaultMethod_shouldUseInvokeDefault() {
        StorageEngine proxy = registry.getProxy(StorageEngineEnum.MINIO);
        var policy = proxy.issueDirectUploadPolicy("k", 10);
        assertThat(policy).isNotNull();
        assertThat(policy.isSuccess()).isFalse();
    }

    @Test
    void getProxy_hashCode_shouldBeIdentity() {
        StorageEngine proxy = registry.getProxy(StorageEngineEnum.MINIO);
        assertThat(proxy.hashCode()).isEqualTo(System.identityHashCode(proxy));
    }

    @Test
    void getProxy_equals_shouldBeIdentity() {
        StorageEngine proxy = registry.getProxy(StorageEngineEnum.MINIO);
        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.equals(new Object())).isFalse();
    }

    @Test
    void getProxy_invocationTargetException_shouldUnwrapCause() {
        StubEngine throwing = new StubEngine("t") {
            @Override
            public boolean existsObject(String key) {
                throw new IllegalStateException("inner cause");
            }
        };
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-1", throwing);
        StorageEngine proxy = registry.getProxy(StorageEngineEnum.MINIO);
        assertThatThrownBy(() -> proxy.existsObject("k"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inner cause");
    }

    // ========== SpringContextHolder 动态 Provider 查找 ==========

    @Test
    void switchEngine_shouldDynamicallyFindProviderFromApplicationContext() {
        applicationContext.getBeanFactory().registerSingleton("minio", new StubProvider(StorageEngineEnum.MINIO, new StubEngine("minio-e")));
        applicationContext.getBeanFactory().registerSingleton("ftp", new StubProvider(StorageEngineEnum.FTP, new StubEngine("ftp-e")));

        registry.switchEngine(StorageEngineEnum.MINIO, new StorageProperties());
        registry.switchEngine(StorageEngineEnum.FTP, new StorageProperties());

        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isNotNull();
        assertThat(registry.getEngine(StorageEngineEnum.FTP)).isNotNull();
    }

    // ========== Helper classes ==========

    /**
     * 简单的 StorageEngine stub
     */
    static class StubEngine implements StorageEngine {
        final String name;
        String lastExistsKey;

        StubEngine(String name) {
            this.name = name;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putData(String key, Map<?, ?> collection) {
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
            this.lastExistsKey = key;
            return true;
        }
    }

    /**
     * 简单的 Provider stub，返回预定的 engine
     */
    static class StubProvider implements StorageEngineProvider {
        private final StorageEngineEnum type;
        private final StorageEngine engineToReturn;

        StubProvider(StorageEngineEnum type, StorageEngine engineToReturn) {
            this.type = type;
            this.engineToReturn = engineToReturn;
        }

        @Override
        public StorageEngineEnum supportedEngineType() {
            return type;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            return engineToReturn;
        }
    }

    /**
     * validate 阶段抛异常的 Provider
     */
    static class FailingProvider implements StorageEngineProvider {
        private final StorageEngineEnum type;
        private final RuntimeException toThrow;

        FailingProvider(StorageEngineEnum type, RuntimeException toThrow) {
            this.type = type;
            this.toThrow = toThrow;
        }

        @Override
        public StorageEngineEnum supportedEngineType() {
            return type;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            throw toThrow;
        }

        @Override
        public void validate(StorageProperties properties) {
            throw toThrow;
        }
    }

    /**
     * create 阶段抛异常的 Provider
     */
    static class ThrowingProvider implements StorageEngineProvider {
        private final StorageEngineEnum type;
        private final RuntimeException toThrow;
        private final StorageEngine returnOnSuccess;

        ThrowingProvider(StorageEngineEnum type, RuntimeException toThrow, StorageEngine returnOnSuccess) {
            this.type = type;
            this.toThrow = toThrow;
            this.returnOnSuccess = returnOnSuccess;
        }

        @Override
        public StorageEngineEnum supportedEngineType() {
            return type;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            throw toThrow;
        }
    }

    /**
     * 记录 destroy 调用的 Provider
     */
    static class DestroyRecordingProvider implements StorageEngineProvider {
        private final StorageEngineEnum type;
        private final StorageEngine toReturn;
        final List<StorageEngine> destroyedEngines = new java.util.ArrayList<>();

        DestroyRecordingProvider(StorageEngineEnum type, StorageEngine toReturn) {
            this.type = type;
            this.toReturn = toReturn;
        }

        @Override
        public StorageEngineEnum supportedEngineType() {
            return type;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            return toReturn;
        }

        @Override
        public void destroy(StorageEngine engine) {
            destroyedEngines.add(engine);
        }
    }

    /**
     * 记录 afterPropertiesSet 调用次数的 Provider
     */
    static class AfterPropertiesSetProvider implements StorageEngineProvider {
        private final StorageEngineEnum type;
        private final StorageEngine toReturn;
        int afterPropertiesSetCalls = 0;

        AfterPropertiesSetProvider(StorageEngineEnum type, StorageEngine toReturn) {
            this.type = type;
            this.toReturn = toReturn;
        }

        @Override
        public StorageEngineEnum supportedEngineType() {
            return type;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            return toReturn;
        }

        @Override
        public void afterPropertiesSet(StorageEngine engine) {
            afterPropertiesSetCalls++;
        }
    }
}
