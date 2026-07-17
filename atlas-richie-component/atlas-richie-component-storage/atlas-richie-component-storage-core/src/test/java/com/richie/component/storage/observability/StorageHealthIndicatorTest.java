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
package com.richie.component.storage.observability;

import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.config.StorageEngineRegistry;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.context.common.api.SpringContextHolder;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageHealthIndicatorTest {

    private AnnotationConfigApplicationContext ctx;
    private StorageEngineRegistry registry;
    private StorageHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.refresh();
        try {
            Field f = SpringContextHolder.class.getDeclaredField("applicationContext");
            f.setAccessible(true);
            f.set(null, ctx);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        registry = new StorageEngineRegistry();
        indicator = new StorageHealthIndicator(registry);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) ctx.close();
        try {
            Field f = SpringContextHolder.class.getDeclaredField("applicationContext");
            f.setAccessible(true);
            f.set(null, null);
        } catch (ReflectiveOperationException ignored) {}
    }

    @Test
    @SuppressWarnings("unchecked")
    void health_whenUninitialized_shouldBeDownWithUninitializedReason() {
        Health health = indicator.health();
        assertThat(health.getStatus().getCode()).isEqualTo(Status.DOWN.getCode());
        Map<String, Object> details = health.getDetails();
        assertThat(details).containsEntry("reason", "uninitialized");
    }

    @Test
    @SuppressWarnings("unchecked")
    void health_whenInitialized_shouldBeUpWithEngineDetails() {
        registry.registerInitialEngine(StorageEngineEnum.MINIO, "minio-1", new StubEngine("minio"));
        registry.registerInitialEngine(StorageEngineEnum.FTP, "ftp-1", new StubEngine("ftp"));

        Health health = indicator.health();
        assertThat(health.getStatus().getCode()).isEqualTo(Status.UP.getCode());
        Map<String, Object> details = health.getDetails();
        assertThat(details).containsEntry("defaultEngine", "MINIO");
        assertThat(details).containsEntry("defaultEngineId", "minio-1");

        Map<String, Object> engines = (Map<String, Object>) details.get("engines");
        assertThat(engines).containsKeys("MINIO", "FTP");
        assertThat(engines.get("MINIO")).isEqualTo("StubEngine");
    }

    static class StubEngine implements StorageEngine {
        final String name;
        StubEngine(String name) { this.name = name; }
        @Override public com.richie.component.storage.bean.UploadResponse putData(@NonNull String k, @NonNull Map<?, ?> c) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putData(@NonNull String k, java.util.@NonNull Collection<?> c) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putData(@NonNull String k, @NonNull Object o) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putObject(@NonNull String k, java.io.@NonNull File f) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putObject(@NonNull String k, java.io.@NonNull InputStream i) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putImage(@NonNull String k, java.io.@NonNull File f, com.richie.component.storage.bean.image.ImageOptions o) { return null; }
        @Override public com.richie.component.storage.bean.UploadResponse putImage(@NonNull String k, java.io.@NonNull InputStream i, com.richie.component.storage.bean.image.ImageOptions o) { return null; }
        @Override public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(@NonNull String k, tools.jackson.core.type.@NonNull TypeReference<T> t) { return null; }
        @Override public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(@NonNull String k, java.io.@NonNull File p, boolean r) { return null; }
        @Override public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(@NonNull String k, @NonNull String t, boolean r) { return null; }
        @Override public boolean existsObject(@NonNull String k) { return true; }
    }
}
