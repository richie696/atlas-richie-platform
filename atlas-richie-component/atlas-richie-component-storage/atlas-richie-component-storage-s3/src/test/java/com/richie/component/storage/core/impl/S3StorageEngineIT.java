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
package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.config.S3AutoConfiguration;
import com.richie.context.common.api.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
@EnabledIf("isDockerAvailable")
@SpringBootTest(
    classes = {S3AutoConfiguration.class, SpringContextHolder.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "platform.component.storage.object.engine=aws_s3",
    "platform.component.storage.object.endpoint=http://127.0.0.1:${minio.port}",
    "platform.component.storage.object.access-key-id=minioadmin",
    "platform.component.storage.object.access-key-secret=minioadmin",
    "platform.component.storage.object.bucket-name=test-bucket",
    "platform.component.storage.object.region=us-east-1",
    "platform.component.storage.object.base-path=test",
    "platform.component.storage.object.auto-create-bucket=true"
})
class S3StorageEngineIT {

    private static final String BUCKET = "test-bucket";
    private static final String BASE_PATH = "test/";

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static GenericContainer<?> rustfsContainer = new GenericContainer<>("rustfs/rustfs:latest")
            .withCommand("/data")
            .withExposedPorts(9000)
            .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
            .withEnv("RUSTFS_SECRET_KEY", "minioadmin");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.storage.object.endpoint",
            () -> "http://127.0.0.1:" + rustfsContainer.getMappedPort(9000));
    }

    @Autowired
    private S3StorageEngine engine;

    @Autowired
    private S3Client s3Client;

    @TempDir
    Path tempDir;

    @Test
    void putObject_file_success() throws IOException {
        Path file = tempDir.resolve("put-file.txt");
        Files.writeString(file, "Hello S3 Integration Test");

        UploadResponse response = engine.putObject("put-file.txt", file.toFile());

        assertThat(response.isSuccess())
                .as("putObject failed: %s", response.getErrorMessage())
                .isTrue();
        assertThat(response.getBucketName()).isEqualTo(BUCKET);

        String realKey = "test/put-file.txt";
        s3Client.headObject(HeadObjectRequest.builder()
                .bucket(BUCKET)
                .key(realKey)
                .build());
    }

    @Test
    void putObject_stream_success() {
        var inputStream = new ByteArrayInputStream("stream upload content".getBytes());

        UploadResponse response = engine.putObject("put-stream.txt", inputStream);

        assertThat(response.isSuccess())
                .as("putObject(stream) failed: %s", response.getErrorMessage())
                .isTrue();
        assertThat(response.getBucketName()).isEqualTo(BUCKET);
    }

    @Test
    void getObject_withReturnData_success() throws IOException {
        String content = "Content to verify with data";
        Path uploadFile = tempDir.resolve("get-data.txt");
        Files.writeString(uploadFile, content);
        engine.putObject("get-data.txt", uploadFile.toFile());

        Path target = tempDir.resolve("downloaded-data.txt");
        Files.createFile(target);
        DownloadResponse<byte[]> response = engine.getObject("get-data.txt", target.toFile(), true);

        assertThat(response.isSuccess())
                .as("getObject failed: %s", response.getErrorMessage())
                .isTrue();
        assertThat(response.getData()).isEqualTo(content.getBytes());
    }

    @Test
    void getObject_withoutReturnData_success() throws IOException {
        String content = "Content to download without data";
        Path uploadFile = tempDir.resolve("get-nodata.txt");
        Files.writeString(uploadFile, content);
        engine.putObject("get-nodata.txt", uploadFile.toFile());

        Path target = tempDir.resolve("downloaded-nodata.txt");
        Files.createFile(target);
        DownloadResponse<byte[]> response = engine.getObject("get-nodata.txt", target.toFile(), false);

        assertThat(response.isSuccess())
                .as("getObject failed: %s", response.getErrorMessage())
                .isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void existsObject_true() throws IOException {
        Path file = tempDir.resolve("exists-true.txt");
        Files.writeString(file, "exists check");
        engine.putObject("exists-true.txt", file.toFile());

        boolean exists = engine.existsObject("exists-true.txt");

        assertThat(exists).isTrue();
    }

    @Test
    void existsObject_false() {
        boolean exists = engine.existsObject("non-existing-key-" + System.nanoTime() + ".txt");

        assertThat(exists).isFalse();
    }

    @Test
    void getResumableObject_success() throws IOException {
        String content = "Resumable download content";
        engine.putObject("resumable.txt", new ByteArrayInputStream(content.getBytes()));

        Path target = tempDir.resolve("resumed.txt");
        Files.createFile(target);
        DownloadResponse<byte[]> response = engine.getResumableObject("resumable.txt", target.toString(), true);

        assertThat(response.isSuccess())
                .as("getResumableObject failed: %s", response.getErrorMessage())
                .isTrue();
        assertThat(response.getData()).isEqualTo(content.getBytes());
    }

    @Test
    void full_uploadDownloadDelete_flow() throws IOException {
        String content = "Full flow content";
        Path uploadFile = tempDir.resolve("full-flow.txt");
        Files.writeString(uploadFile, content);
        UploadResponse uploadResponse = engine.putObject("full-flow.txt", uploadFile.toFile());
        assertThat(uploadResponse.isSuccess())
                .as("upload failed: %s", uploadResponse.getErrorMessage())
                .isTrue();

        assertThat(engine.existsObject("full-flow.txt")).isTrue();

        Path target = tempDir.resolve("full-flow-downloaded.txt");
        Files.createFile(target);
        DownloadResponse<byte[]> downloadResponse = engine.getObject("full-flow.txt", target.toFile(), true);

        assertThat(downloadResponse.isSuccess())
                .as("download failed: %s", downloadResponse.getErrorMessage())
                .isTrue();
        assertThat(downloadResponse.getData()).isEqualTo(content.getBytes());
    }
}
