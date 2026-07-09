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
package com.richie.component.storage.local.config.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.local.config.support.AbstractStorageLocalIntegrationTest;
import com.richie.component.storage.local.repository.entity.FileMetadata;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageEngineIT extends AbstractStorageLocalIntegrationTest {

    @Test
    void putData_persistsFileAndMetadata() {
        var uploaded = engine.putData("it/docs/hello.json", Map.of("msg", "hi"));
        assertThat(uploaded.isSuccess()).isTrue();
        assertThat(engine.existsObject("it/docs/hello.json")).isTrue();

        Long count = fileMetadataMapper.selectCount(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getKeyPath, "it/docs/hello.json"));
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void putData_recordAndListOverloads_roundTrip() {
        assertThat(engine.putData("it/payload.json", new DemoPayload("object")).isSuccess()).isTrue();
        assertThat(engine.putData("it/list.json", List.of("a", "b")).isSuccess()).isTrue();

        DownloadResponse<DemoPayload> objectResponse = engine.getData("it/payload.json", new TypeReference<DemoPayload>() {
        });
        assertThat(objectResponse.isSuccess()).isTrue();
        assertThat(objectResponse.getData().msg()).isEqualTo("object");

        DownloadResponse<List<String>> listResponse = engine.getData("it/list.json", new TypeReference<List<String>>() {
        });
        assertThat(listResponse.isSuccess()).isTrue();
        assertThat(listResponse.getData()).containsExactly("a", "b");
    }

    @Test
    void putObject_streamUpload_canBeReadBack() {
        byte[] bytes = "binary-content".getBytes(StandardCharsets.UTF_8);
        var uploaded = engine.putObject("it/bin/data.bin", new ByteArrayInputStream(bytes));
        assertThat(uploaded.isSuccess()).isTrue();

        var downloaded = engine.getObject("it/bin/data.bin", new java.io.File(System.getProperty("java.io.tmpdir")), true);
        assertThat(downloaded.isSuccess()).isTrue();
        assertThat(downloaded.getData()).isEqualTo(bytes);
    }

    @Test
    void dedupe_skipsRewriteWhenHashUnchanged() throws Exception {
        engine.putData("it/dedupe.json", Map.of("v", "1"));
        FileMetadata before = fileMetadataMapper.selectOne(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getKeyPath, "it/dedupe.json"));
        assertThat(before).isNotNull();

        var second = engine.putData("it/dedupe.json", Map.of("v", "1"));
        assertThat(second.isSuccess()).isTrue();

        FileMetadata after = fileMetadataMapper.selectOne(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getKeyPath, "it/dedupe.json"));
        assertThat(after.getHashValue()).isEqualTo(before.getHashValue());
    }

    @Test
    void clearFileCaches_forcesFilesystemLookup() {
        engine.putData("it/cache.json", Map.of("cached", true));
        assertThat(engine.existsObject("it/cache.json")).isTrue();

        engine.clearFileCaches("it/cache.json");
        assertThat(engine.existsObject("it/cache.json")).isTrue();
    }

    record DemoPayload(String msg) {
    }
}
