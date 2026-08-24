/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.storage.core.impl;

import cn.richie696.context.common.api.SpringContextHolder;
import cn.richie696.testing.env.TestEnv;
import cn.richie696.component.storage.bean.DownloadResponse;
import cn.richie696.component.storage.bean.UploadResponse;
import cn.richie696.component.storage.config.MinioAutoConfiguration;
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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vault -> StorageProperties -> real MinIO roundtrip.
 *
 * <p>The MinIO endpoint and bucket are test infrastructure values; the two
 * credentials must arrive from the Vault bundle before StorageProperties binds.
 */
@SpringBootTest(
        classes = {MinioAutoConfiguration.class, SpringContextHolder.class},
        properties = {
                "spring.application.name=orders",
                "platform.component.secret.enabled=true",
                "platform.component.secret.strict-mode=true",
                "platform.component.secret.property-source.application=orders",
                "platform.component.secret.property-source.environment=e2e",
                "platform.component.secret.refresh.enabled=false",
                "platform.component.secret.vault.endpoint=${ATLAS_SECRET_VAULT_ENDPOINT:http://127.0.0.1:8200}",
                "platform.component.secret.vault.authentication.type=token",
                "platform.component.secret.vault.authentication.token=${ATLAS_SECRET_VAULT_TOKEN:}",
                "platform.component.secret.vault.kv.mount=kv",
                "platform.component.secret.vault.transit.mount=transit",
                "platform.component.storage.object.engine=minio",
                "platform.component.storage.object.region=us-east-1"
        })
@Testcontainers
@EnabledIf("cn.richie696.component.storage.core.impl.MinioVaultStorageIntegrationIT#isEnabled")
class MinioVaultStorageIntegrationIT {

    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET = "vault-storage-e2e";

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY);

    @Autowired
    private MinioStorageEngine engine;

    @TempDir
    Path tempDir;

    static boolean isEnabled() {
        try {
            return TestEnv.isTruthy("ATLAS_SECRET_VAULT_E2E", "atlas.secret.vault.e2e")
                    && TestEnv.firstResolved(
                    new String[]{"ATLAS_SECRET_VAULT_TOKEN"},
                    new String[]{"atlas.secret.vault.token"}) != null
                    && DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.storage.object.endpoint",
                () -> "http://127.0.0.1:" + minio.getMappedPort(9000));
        registry.add("platform.component.storage.object.bucket-name", () -> BUCKET);
    }

    @Test
    void vaultCredentialsDriveRealMinioRoundtrip() throws Exception {
        String content = "secret-backed-storage";
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, content);

        UploadResponse upload = engine.putObject("vault-e2e.txt", source.toFile());
        assertThat(upload.isSuccess()).isTrue();

        Path target = tempDir.resolve("target.txt");
        Files.createFile(target);
        DownloadResponse<byte[]> download = engine.getObject("vault-e2e.txt", target.toFile(), true);
        assertThat(download.isSuccess()).isTrue();
        assertThat(download.getData()).isEqualTo(content.getBytes());
    }
}
