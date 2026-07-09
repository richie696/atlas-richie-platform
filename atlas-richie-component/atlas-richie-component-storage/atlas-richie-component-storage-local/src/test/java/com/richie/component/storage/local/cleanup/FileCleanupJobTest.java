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
package com.richie.component.storage.local.cleanup;

import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.local.repository.mapper.FileMetadataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FileCleanupJobTest {

    @TempDir
    Path tempDir;

    @Test
    void cleanup_dryRun_shouldNotDeleteFiles() throws Exception {
        Path stale = tempDir.resolve("old.txt");
        Files.writeString(stale, "stale");
        Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.from(
                Instant.now().minus(400, ChronoUnit.DAYS)));

        LocalConfig local = new LocalConfig();
        local.setPath(tempDir.toString());
        var cleanup = new LocalConfig.Cleanup();
        cleanup.setEnabled(true);
        cleanup.setRetentionDays(30);
        cleanup.setDryRun(true);
        local.setCleanup(cleanup);

        FileCleanupJob job = new FileCleanupJob(local, Mockito.mock(FileMetadataMapper.class));
        job.cleanup();

        assertThat(Files.exists(stale)).isTrue();
    }

    @Test
    void cleanup_whenDisabled_shouldNoop() {
        LocalConfig local = new LocalConfig();
        local.setPath(tempDir.toString());
        local.getCleanup().setEnabled(false);
        FileCleanupJob job = new FileCleanupJob(local, Mockito.mock(FileMetadataMapper.class));
        job.cleanup();
    }

    @Test
    void cleanup_whenEnabled_deletesStaleFile() throws Exception {
        Path stale = tempDir.resolve("delete-me.txt");
        Files.writeString(stale, "old");
        var epoch = java.nio.file.attribute.FileTime.from(Instant.EPOCH);
        Files.setLastModifiedTime(stale, epoch);
        try {
            Files.setAttribute(stale, "lastAccessTime", epoch);
        } catch (Exception ignored) {
        }

        LocalConfig local = new LocalConfig();
        local.setPath(tempDir.toString());
        var cleanup = new LocalConfig.Cleanup();
        cleanup.setEnabled(true);
        cleanup.setRetentionDays(30);
        cleanup.setDryRun(false);
        cleanup.setMaxDeletePerRun(10);
        local.setCleanup(cleanup);

        FileCleanupJob job = new FileCleanupJob(local, Mockito.mock(FileMetadataMapper.class));
        job.cleanup();

        assertThat(Files.exists(stale)).isFalse();
    }

    @Test
    void cleanup_whenPathMissing_skipsSafely() {
        LocalConfig local = new LocalConfig();
        local.setPath("  ");
        local.getCleanup().setEnabled(true);
        new FileCleanupJob(local, Mockito.mock(FileMetadataMapper.class)).cleanup();
    }

    @Test
    void cleanup_whenRootMissing_skipsSafely() {
        LocalConfig local = new LocalConfig();
        local.setPath(tempDir.resolve("missing-root").toString());
        local.getCleanup().setEnabled(true);
        new FileCleanupJob(local, Mockito.mock(FileMetadataMapper.class)).cleanup();
    }

    @Test
    void cleanup_withRemoveMetadata_flagEnabled_runsWithoutException() throws Exception {
        Path stale = tempDir.resolve("meta.txt");
        Files.writeString(stale, "x");
        var epoch = java.nio.file.attribute.FileTime.from(Instant.EPOCH);
        Files.setLastModifiedTime(stale, epoch);

        LocalConfig local = new LocalConfig();
        local.setPath(tempDir.toString());
        var cleanup = new LocalConfig.Cleanup();
        cleanup.setEnabled(true);
        cleanup.setRetentionDays(0);
        cleanup.setDryRun(true);
        cleanup.setRemoveMetadata(true);
        local.setCleanup(cleanup);

        FileMetadataMapper mapper = Mockito.mock(FileMetadataMapper.class);
        new FileCleanupJob(local, mapper).cleanup();
    }
}
