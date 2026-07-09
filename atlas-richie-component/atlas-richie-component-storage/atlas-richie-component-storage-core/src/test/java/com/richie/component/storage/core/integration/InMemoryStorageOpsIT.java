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
package com.richie.component.storage.core.integration;

import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.core.support.InMemoryStorageEngineSupport;
import com.richie.component.storage.core.support.StorageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@StorageIntegrationTest
class InMemoryStorageOpsIT {

    @TempDir
    Path tempDir;

    @Test
    void abstractEngine_putData_roundTripThroughStub() {
        var engine = InMemoryStorageEngineSupport.create(tempDir);
        UploadResponse uploaded = engine.putData("note.json", java.util.Map.of("k", "v"));
        assertThat(uploaded.isSuccess()).isTrue();
        assertThat(uploaded.getKey()).contains("note.json");
    }
}
