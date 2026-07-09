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
package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.DownloadResponse;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.config.MinioAutoConfiguration;
import com.richie.context.common.api.SpringContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minio 存储引擎集成测试 —— 使用 Testcontainers 启动真实 MinIO 容器。
 * <p>
 * 需要在运行环境中安装 Docker。集成测试使用 maven-failsafe-plugin 执行，
 * 默认不包含在 {@code mvn test} 中，需要通过 {@code mvn verify} 运行。
 * JaCoCo 覆盖率结果会与单元测试结果合并。
 *
 * @author richie696
 */
@SpringBootTest(classes = {
        MinioAutoConfiguration.class,
        SpringContextHolder.class
})
@Testcontainers
@EnabledIf("isDockerAvailable")
class MinioStorageEngineIT {

    private static final String BUCKET = "it-test-bucket";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static GenericContainer<?> minioContainer = new GenericContainer<>("minio/minio:latest")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.storage.object.endpoint",
                () -> "http://127.0.0.1:" + minioContainer.getMappedPort(9000));
        registry.add("platform.component.storage.object.access-key-id", () -> ACCESS_KEY);
        registry.add("platform.component.storage.object.access-key-secret", () -> SECRET_KEY);
        registry.add("platform.component.storage.object.bucket-name", () -> BUCKET);
        registry.add("platform.component.storage.object.engine", () -> "minio");
        registry.add("platform.component.storage.object.region", () -> "us-east-1");
    }

    @Autowired
    private MinioStorageEngine engine;

    @TempDir
    Path tempDir;

    @Test
    void putObject_withFile_shouldSucceed() throws Exception {
        Path file = tempDir.resolve("test-file.txt");
        Files.writeString(file, "Hello MinIO Integration Test");

        UploadResponse response = engine.putObject("test-file.txt", file.toFile());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo(BUCKET);
        assertThat(response.getRequestId()).isNotBlank();
    }

    @Test
    void putObject_withInputStream_shouldSucceed() {
        var inputStream = new ByteArrayInputStream("stream content".getBytes());

        UploadResponse response = engine.putObject("test-stream.txt", inputStream);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo(BUCKET);
    }

    @Test
    void putObject_and_getObject_shouldMatchContent() throws Exception {
        String content = "Content to verify roundtrip";
        Path uploadFile = tempDir.resolve("roundtrip.txt");
        Files.writeString(uploadFile, content);

        UploadResponse uploadResponse = engine.putObject("roundtrip.txt", uploadFile.toFile());
        assertThat(uploadResponse.isSuccess()).isTrue();

        Path target = tempDir.resolve("downloaded.txt");
        Files.createFile(target);  // engine.getObject 要求文件已存在（canWrite 检查）
        DownloadResponse<byte[]> downloadResponse = engine.getObject("roundtrip.txt", target.toFile(), true);

        assertThat(downloadResponse.isSuccess()).isTrue();
        assertThat(downloadResponse.getData()).isEqualTo(content.getBytes());
    }

    @Test
    void getObject_withoutReturnData_shouldNotReturnData() throws Exception {
        String content = "No data return";
        Path uploadFile = tempDir.resolve("nodata.txt");
        Files.writeString(uploadFile, content);
        engine.putObject("nodata.txt", uploadFile.toFile());

        Path target = tempDir.resolve("downloaded-nodata.txt");
        Files.createFile(target);  // engine.getObject 要求文件已存在
        DownloadResponse<byte[]> response = engine.getObject("nodata.txt", target.toFile(), false);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(target).exists();
    }

    @Test
    void existsObject_withExistingKey_shouldReturnTrue() {
        engine.putObject("exists-check.txt", new ByteArrayInputStream("check".getBytes()));

        boolean exists = engine.existsObject("exists-check.txt");

        assertThat(exists).isTrue();
    }

    @Test
    void existsObject_withNonExistingKey_shouldReturnFalse() {
        boolean exists = engine.existsObject("non-existing-key.txt");

        assertThat(exists).isFalse();
    }

    @Test
    void getResumableObject_shouldSucceed() throws Exception {
        String content = "Resumable content for testing";
        engine.putObject("resumable.txt", new ByteArrayInputStream(content.getBytes()));

        Path target = tempDir.resolve("resumed.txt");
        Files.createFile(target);  // getResumableObject → getObject 要求文件已存在
        DownloadResponse<byte[]> response = engine.getResumableObject("resumable.txt", target.toString(), true);

        assertThat(response.isSuccess())
                .as("getResumableObject failed: " + response.getErrorMessage())
                .isTrue();
        assertThat(response.getData()).isEqualTo(content.getBytes());
    }
}
