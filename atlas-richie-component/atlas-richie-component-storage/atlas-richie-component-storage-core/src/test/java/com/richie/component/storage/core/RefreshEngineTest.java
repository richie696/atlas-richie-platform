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
import com.richie.context.common.api.SpringContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StorageEngineRegistry.refreshEngine 回滚语义测试
 * <p>
 * 覆盖：
 * <ul>
 *     <li>替换已注册引擎并保留旧 delegate 的强引用直到新引擎就绪</li>
 *     <li>未初始化的引擎调用 refreshEngine 抛 IllegalStateException</li>
 *     <li>afterPropertiesSet 失败时回滚（旧 delegate 保持不变）</li>
 *     <li>旧引擎 destroy 失败仅记录 warn，不影响新引擎生效</li>
 *     <li>参数校验（null engineType / properties 抛 NPE）</li>
 * </ul>
 */
class RefreshEngineTest {

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

    @Test
    void refreshEngine_shouldReplaceDelegateAndDestroyOld() {
        StubEngine oldEngine = new StubEngine("old");
        StubEngine newEngine = new StubEngine("new");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-old", oldEngine);

        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        provider.engineToReturn = newEngine;
        provider.afterPropertiesSetBehavior = () -> { /* no-op */ };
        provider.destroyBehavior = engine -> { /* ok */ };
        registerProvider(provider);

        StorageProperties props = new StorageProperties();
        StorageEngine returned = registry.refreshEngine(StorageEngineEnum.MINIO, props);

        assertThat(returned).isSameAs(newEngine);
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(newEngine);
        assertThat(provider.destroyCallCount.get()).isEqualTo(1);
        assertThat(provider.destroyedEngines).contains(oldEngine);
    }

    @Test
    void refreshEngine_uninitialized_shouldThrowIllegalState() {
        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        registerProvider(provider);

        assertThatThrownBy(() -> registry.refreshEngine(StorageEngineEnum.MINIO, new StorageProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法刷新尚未初始化");
    }

    @Test
    void refreshEngine_afterPropertiesSetFails_shouldKeepOldEngine() {
        StubEngine oldEngine = new StubEngine("old");
        StubEngine newEngine = new StubEngine("new");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-old", oldEngine);

        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        provider.engineToReturn = newEngine;
        provider.afterPropertiesSetBehavior = () -> {
            throw new IllegalStateException("init failed");
        };
        provider.destroyBehavior = engine -> { /* cleanup attempt */ };
        registerProvider(provider);

        assertThatThrownBy(() -> registry.refreshEngine(StorageEngineEnum.MINIO, new StorageProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("init failed");

        // Old delegate is preserved
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(oldEngine);
        // New engine was created but should be destroyed (cleanup of failed init)
        assertThat(provider.destroyedEngines).contains(newEngine);
    }

    @Test
    void refreshEngine_oldDestroyFails_shouldStillApplyNewEngineAndLogWarn() {
        StubEngine oldEngine = new StubEngine("old");
        StubEngine newEngine = new StubEngine("new");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-old", oldEngine);

        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        provider.engineToReturn = newEngine;
        provider.afterPropertiesSetBehavior = () -> { /* ok */ };
        provider.destroyBehavior = engine -> {
            if (engine == oldEngine) {
                throw new RuntimeException("destroy failed");
            }
        };
        registerProvider(provider);

        // Should NOT throw — destroy failure is contained
        StorageEngine returned = registry.refreshEngine(StorageEngineEnum.MINIO, new StorageProperties());
        assertThat(returned).isSameAs(newEngine);
        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(newEngine);
    }

    @Test
    void refreshEngine_nullEngineType_shouldThrowNpe() {
        assertThatThrownBy(() -> registry.refreshEngine(null, new StorageProperties()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void refreshEngine_nullProperties_shouldThrowNpe() {
        assertThatThrownBy(() -> registry.refreshEngine(StorageEngineEnum.MINIO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void refreshEngine_shouldUpdateDefaultEngineForSameType() {
        StubEngine oldEngine = new StubEngine("old");
        StubEngine newEngine = new StubEngine("new");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-initial", oldEngine);
        assertThat(registry.getDefaultEngine()).isSameAs(oldEngine);

        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        provider.engineToReturn = newEngine;
        provider.afterPropertiesSetBehavior = () -> { };
        provider.destroyBehavior = engine -> { };
        registerProvider(provider);

        registry.refreshEngine(StorageEngineEnum.MINIO, new StorageProperties());
        assertThat(registry.getDefaultEngine()).isSameAs(newEngine);
    }

    @Test
    void refreshEngine_withAuditArgs_shouldAcceptNullActorAndReason() {
        StubEngine oldEngine = new StubEngine("old");
        StubEngine newEngine = new StubEngine("new");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-old", oldEngine);

        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        provider.engineToReturn = newEngine;
        provider.afterPropertiesSetBehavior = () -> { };
        provider.destroyBehavior = engine -> { };
        registerProvider(provider);

        StorageEngine returned = registry.refreshEngine(
                StorageEngineEnum.MINIO, new StorageProperties(), null, null);
        assertThat(returned).isSameAs(newEngine);
    }

    @Test
    void refreshEngine_noProvider_shouldThrowIllegalState() {
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-old", new StubEngine("old"));

        assertThatThrownBy(() -> registry.refreshEngine(StorageEngineEnum.MINIO, new StorageProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider");
    }

    @Test
    void refreshEngine_providerValidationFails_shouldThrowAndKeepOldEngine() {
        StubEngine oldEngine = new StubEngine("old");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-old", oldEngine);

        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        provider.validateBehavior = () -> {
            throw new IllegalArgumentException("validation failed");
        };
        registerProvider(provider);

        assertThatThrownBy(() -> registry.refreshEngine(StorageEngineEnum.MINIO, new StorageProperties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation failed");

        assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isSameAs(oldEngine);
    }

    @Test
    void refreshEngine_defaultOverload_shouldUseSystemActor() {
        StubEngine oldEngine = new StubEngine("old");
        StubEngine newEngine = new StubEngine("new");
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "m-old", oldEngine);

        ProgrammableProvider provider = new ProgrammableProvider(StorageEngineEnum.MINIO);
        provider.engineToReturn = newEngine;
        provider.afterPropertiesSetBehavior = () -> { };
        provider.destroyBehavior = engine -> { };
        registerProvider(provider);

        StorageEngine returned = registry.refreshEngine(StorageEngineEnum.MINIO, new StorageProperties());
        assertThat(returned).isSameAs(newEngine);
    }

    /**
     * 简单的 StorageEngine stub
     */
    static class StubEngine implements StorageEngine {
        final String name;

        StubEngine(String name) {
            this.name = name;
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putData(String key, java.util.Map<?, ?> collection) { return null; }

        @Override
        public com.richie.component.storage.bean.UploadResponse putData(String key, java.util.Collection<?> collection) { return null; }

        @Override
        public com.richie.component.storage.bean.UploadResponse putData(String key, Object object) { return null; }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.File file) { return null; }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.InputStream inputStream) { return null; }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.File file,
                                                                          com.richie.component.storage.bean.image.ImageOptions options) { return null; }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.InputStream inputStream,
                                                                          com.richie.component.storage.bean.image.ImageOptions options) { return null; }

        @Override
        public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(String key,
                                                                                 tools.jackson.core.type.TypeReference<T> typeReference) { return null; }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(String key, java.io.File targetPath,
                                                                                   boolean returnData) { return null; }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(String key, String targetPath,
                                                                                            boolean returnData) { return null; }

        @Override
        public boolean existsObject(String key) { return true; }
    }

    /**
     * 可编程 Provider：测试可控制 validate/create/afterPropertiesSet/destroy 的行为。
     */
    static class ProgrammableProvider implements StorageEngineProvider {
        private final StorageEngineEnum type;
        StorageEngine engineToReturn;
        Runnable validateBehavior = () -> { };
        Runnable afterPropertiesSetBehavior = () -> { };
        java.util.function.Consumer<StorageEngine> destroyBehavior = e -> { };
        final AtomicInteger destroyCallCount = new AtomicInteger();
        final java.util.List<StorageEngine> destroyedEngines = new java.util.ArrayList<>();

        ProgrammableProvider(StorageEngineEnum type) {
            this.type = type;
        }

        @Override
        public StorageEngineEnum supportedEngineType() {
            return type;
        }

        @Override
        public void validate(StorageProperties properties) {
            validateBehavior.run();
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            return engineToReturn;
        }

        @Override
        public void afterPropertiesSet(StorageEngine engine) {
            afterPropertiesSetBehavior.run();
        }

        @Override
        public void destroy(StorageEngine engine) {
            destroyCallCount.incrementAndGet();
            destroyedEngines.add(engine);
            destroyBehavior.accept(engine);
        }
    }
}