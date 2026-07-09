/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.local.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.local.repository.entity.FileMetadata;
import com.richie.component.storage.local.repository.mapper.FileMetadataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalStorageEngineTest {

    @Mock
    private FileMetadataMapper fileMetadataMapper;
    @Mock
    private ValueOps valueOps;
    @Mock
    private KeyOps keyOps;
    @Mock
    private StructOps structOps;

    @TempDir
    Path tempDir;

    private StorageProperties properties;
    private LocalConfig localConfig;
    private LocalStorageEngine engine;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        localConfig = new LocalConfig();
        localConfig.setPath(tempDir.toString());
        properties.setLocal(localConfig);
        engine = new LocalStorageEngine(properties, localConfig);
        engine.setFileMetadataMapper(fileMetadataMapper);
        when(fileMetadataMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    }

    @Test
    void putData_andExistsObject_shouldWriteAndDetectFile() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);

            var uploaded = engine.putData("hello.json", Map.of("msg", "hi"));
            assertThat(uploaded.isSuccess()).isTrue();

            when(valueOps.get(startsWith("file:exists:"), eq(Boolean.class))).thenReturn(null);
            assertThat(engine.existsObject("hello.json")).isTrue();
        });
    }

    @Test
    void putData_withListUsesCollectionOverload() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            assertThat(engine.putData("list.json", List.of("a", "b")).isSuccess()).isTrue();
        });
    }

    @Test
    void putData_withRecordUsesObjectOverload() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            assertThat(engine.putData("payload.json", new DemoPayload("hi")).isSuccess()).isTrue();
            assertThat(engine.existsObject("payload.json")).isTrue();
        });
    }

    @Test
    void putObject_rejectsPathTraversal() {
        var response = engine.putObject("../escape.txt", new ByteArrayInputStream("x".getBytes()));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("path traversal");
    }

    @Test
    void putObject_streamUpload_persistsBinaryContent() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            byte[] bytes = "binary-content".getBytes(StandardCharsets.UTF_8);

            var uploaded = engine.putObject("bin/data.bin", new ByteArrayInputStream(bytes));
            assertThat(uploaded.isSuccess()).isTrue();
            assertThat(engine.existsObject("bin/data.bin")).isTrue();
        });
    }

    @Test
    void putObject_fromFile_readsAndStores() throws Exception {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            Path source = tempDir.resolve("source.txt");
            try {
                Files.writeString(source, "from-file");
                var uploaded = engine.putObject("upload/from-file.txt", source.toFile());
                assertThat(uploaded.isSuccess()).isTrue();
                assertThat(Files.readString(tempDir.resolve("upload/from-file.txt"))).isEqualTo("from-file");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void putObject_fromMissingFile_returnsFailure() {
        var uploaded = engine.putObject("missing.txt", new File(tempDir.resolve("nope.txt").toString()));
        assertThat(uploaded.isSuccess()).isFalse();
        assertThat(uploaded.getErrorMessage()).isNotBlank();
    }

    @Test
    void putData_rejectsUnsafeKeys() {
        assertThat(engine.putData("../bad.json", Map.of("v", 1)).isSuccess()).isFalse();
        assertThat(engine.putData("/abs.json", Map.of("v", 1)).isSuccess()).isFalse();
        assertThat(engine.putData("bad:drive.json", Map.of("v", 1)).isSuccess()).isFalse();
    }

    @Test
    void putData_rejectsOversizedStringPayload() {
        localConfig.getCache().setContentMaxSize(4L);
        var response = engine.putData("large.json", Map.of("payload", "12345"));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("too large");
    }

    @Test
    void putData_skipsRewriteWhenHashUnchanged() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            engine.putData("dedupe.json", Map.of("v", "1"));
            try {
                long beforeSize = Files.size(tempDir.resolve("dedupe.json"));
                var second = engine.putData("dedupe.json", Map.of("v", "1"));
                assertThat(second.isSuccess()).isTrue();
                assertThat(Files.size(tempDir.resolve("dedupe.json"))).isEqualTo(beforeSize);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void getData_returnsCachedContentWhenPresent() {
        withGlobalCache(() -> {
            when(valueOps.get("file:content:cached.json", String.class)).thenReturn("{\"msg\":\"cached\"}");

            DownloadResponse<Map<String, String>> response = engine.getData("cached.json", new TypeReference<>() {
            });
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsEntry("msg", "cached");
        });
    }

    @Test
    void getData_fallsBackToFilesystemWhenCacheMisses() {
        withGlobalCache(() -> {
            when(valueOps.get(startsWith("file:content:"), eq(String.class))).thenReturn(null);
            engine.putData("fs.json", Map.of("msg", "from-disk"));

            DownloadResponse<Map<String, String>> response = engine.getData("fs.json", new TypeReference<>() {
            });
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsEntry("msg", "from-disk");
        });
    }

    @Test
    void getData_returnsNotFoundWhenMissing() {
        withGlobalCache(() -> {
            when(valueOps.get(startsWith("file:content:"), eq(String.class))).thenReturn(null);

            DownloadResponse<Map<String, String>> response = engine.getData("missing.json", new TypeReference<>() {
            });
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("not found");
        });
    }

    @Test
    void existsObject_returnsCachedValueWithoutFilesystemLookup() {
        withGlobalCache(() -> {
            when(valueOps.get("file:exists:cached-only.txt", Boolean.class)).thenReturn(true);
            assertThat(engine.existsObject("cached-only.txt")).isTrue();
        });
    }

    @Test
    void getObject_readsBytesFromFilesystem() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            engine.putObject("obj.bin", new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)));

            DownloadResponse<byte[]> response = engine.getObject("obj.bin", tempDir.toFile(), true);
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    void getObject_returnsNotFoundWhenMissing() {
        DownloadResponse<byte[]> response = engine.getObject("missing.bin", tempDir.toFile(), true);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("not found");
    }

    @Test
    void getResumableObject_delegatesToGetObject() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            engine.putObject("resume.bin", new ByteArrayInputStream("resume".getBytes(StandardCharsets.UTF_8)));

            DownloadResponse<byte[]> response = engine.getResumableObject("resume.bin", tempDir.toString(), true);
            assertThat(response.isSuccess()).isTrue();
            assertThat(new String(response.getData(), StandardCharsets.UTF_8)).isEqualTo("resume");
        });
    }

    @Test
    void clearFileCaches_removesAllRelatedKeys() {
        withGlobalCache(() -> {
            engine.clearFileCaches("cache-me.json");
            verify(keyOps).removeCache("file:exists:cache-me.json");
            verify(keyOps).removeCache("file:content:cache-me.json");
            verify(keyOps).removeCache("file:metadata:cache-me.json");
        });
    }

    @Test
    void clearFileCachesBatch_clearsEachKey() {
        withGlobalCache(() -> {
            engine.clearFileCachesBatch(List.of("a.json", "b.json"));
            verify(keyOps, times(2)).removeCache(startsWith("file:exists:"));
            verify(keyOps, times(2)).removeCache(startsWith("file:content:"));
            verify(keyOps, times(2)).removeCache(startsWith("file:metadata:"));
        });
    }

    @Test
    void getObject_rejectsNonWritableTargetDirectory() {
        File readOnlyDir = mock(File.class);
        when(readOnlyDir.canWrite()).thenReturn(false);

        DownloadResponse<byte[]> response = engine.getObject("any.bin", readOnlyDir, true);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("permission");
    }

    @Test
    void putObject_streamDedupe_skipsRewriteWhenHashUnchanged() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            byte[] bytes = "same-stream".getBytes(StandardCharsets.UTF_8);

            assertThat(engine.putObject("stream-dedupe.bin", new ByteArrayInputStream(bytes)).isSuccess()).isTrue();
            try {
                long beforeSize = Files.size(tempDir.resolve("stream-dedupe.bin"));
                assertThat(engine.putObject("stream-dedupe.bin", new ByteArrayInputStream(bytes)).isSuccess()).isTrue();
                assertThat(Files.size(tempDir.resolve("stream-dedupe.bin"))).isEqualTo(beforeSize);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void putObject_rejectsOversizedStream() {
        localConfig.getCache().setContentMaxSize(4L);
        byte[] bytes = "12345".getBytes(StandardCharsets.UTF_8);

        var response = engine.putObject("large.bin", new ByteArrayInputStream(bytes));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("too large");
    }

    @Test
    void getData_updatesContentCacheAfterFilesystemRead() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            when(valueOps.get(startsWith("file:content:"), eq(String.class))).thenReturn(null);
            engine.putData("cache-on-read.json", Map.of("msg", "disk"));

            reset(valueOps);
            when(valueOps.get("file:content:cache-on-read.json", String.class)).thenReturn(null);

            DownloadResponse<Map<String, String>> response = engine.getData("cache-on-read.json", new TypeReference<>() {
            });
            assertThat(response.isSuccess()).isTrue();
            verify(valueOps).set(eq("file:content:cache-on-read.json"), anyString(), anyLong());
        });
    }

    @Test
    void getData_ignoresCorruptedCacheAndReadsFile() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            try {
                Files.writeString(tempDir.resolve("broken.json"), "{\"msg\":\"ok\"}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            when(valueOps.get("file:content:broken.json", String.class)).thenReturn("{not-json");

            DownloadResponse<Map<String, String>> response = engine.getData("broken.json", new TypeReference<>() {
            });
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsEntry("msg", "ok");
        });
    }

    @Test
    void getContentMaxSize_usesDefaultWhenCacheConfigMissing() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            localConfig.setCache(null);
            assertThat(engine.putData("default-limit.json", Map.of("tiny", "x")).isSuccess()).isTrue();
        });
    }

    @Test
    void upsertMetadata_insertUsesEmptyOriginalNameWhenNull() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            when(fileMetadataMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThat(engine.putObject("raw.bin", new ByteArrayInputStream("x".getBytes())).isSuccess()).isTrue();
            verify(fileMetadataMapper).insert(any(FileMetadata.class));
        });
    }

    @Test
    void putImage_isUnsupported() {
        assertThatThrownBy(() -> engine.putImage("img.png", tempDir.toFile(), null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> engine.putImage("img.png", new ByteArrayInputStream(new byte[0]), null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void upsertMetadata_updatesExistingRow() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            when(fileMetadataMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThat(engine.putData("update-meta.json", Map.of("v", 1)).isSuccess()).isTrue();
            verify(fileMetadataMapper).update(any(), any());
        });
    }

    @Test
    void upsertMetadata_swallowsMapperErrors() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            when(fileMetadataMapper.selectCount(any(LambdaQueryWrapper.class))).thenThrow(new RuntimeException("db down"));

            assertThat(engine.putData("meta-error.json", Map.of("v", 1)).isSuccess()).isTrue();
        });
    }

    @Test
    void getData_returnsErrorWhenFilesystemReadFails() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            when(valueOps.get(startsWith("file:content:"), eq(String.class))).thenReturn(null);
            try {
                Files.createDirectories(tempDir.resolve("dir-instead-of-file"));
                DownloadResponse<Map<String, String>> response = engine.getData("dir-instead-of-file", new TypeReference<>() {
                });
                assertThat(response.isSuccess()).isFalse();
                assertThat(response.getErrorMessage()).isNotBlank();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void getObject_returnsErrorWhenFilesystemReadFails_dueToDirectory() {
        try {
            Files.createDirectories(tempDir.resolve("dir-instead-of-file"));
            DownloadResponse<byte[]> response = engine.getObject("dir-instead-of-file", tempDir.toFile(), true);
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).isNotBlank();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void putData_returnsErrorWhenPathIsDirectory() {
        withGlobalCache(() -> {
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            try {
                Files.createDirectories(tempDir.resolve("dir-path"));
                var response = engine.putData("dir-path", Map.of("msg", "should-fail"));
                assertThat(response.isSuccess()).isFalse();
                assertThat(response.getErrorMessage()).isNotBlank();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void withGlobalCache(Runnable action) {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            cache.when(GlobalCache::key).thenReturn(keyOps);
            cache.when(GlobalCache::struct).thenReturn(structOps);
            action.run();
        }
    }

    record DemoPayload(String msg) {
    }
}
