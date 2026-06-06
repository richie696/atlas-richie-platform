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
