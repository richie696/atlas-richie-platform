/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package cn.richie696.component.storage.core.impl;

import cn.richie696.component.storage.bean.ObjectConfig;
import cn.richie696.component.storage.config.StorageProperties;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureBlobStorageEngineTest {

    @Mock
    private BlobContainerClient containerClient;
    @Mock
    private BlobClient blobClient;
    @Mock
    private BlobProperties blobProperties;
    @TempDir
    private Path tempDir;

    private StorageProperties properties;
    private AzureBlobStorageEngine engine;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.getObject().setEndpoint("account.blob.core.windows.net");
        properties.getObject().setBucketName("container");
        properties.getObject().setBasePath("base");
        engine = new AzureBlobStorageEngine(properties);
        engine.setClientOverride(containerClient);
        lenient().when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
    }

    @Test
    void publicObjectUrl_usesAzureContainerPathAndBasePath() {
        StorageProperties properties = new StorageProperties();
        ObjectConfig objectConfig = properties.getObject();
        objectConfig.setEndpoint("account.blob.core.windows.net");
        objectConfig.setBucketName("container");
        objectConfig.setBasePath("base");

        AzureBlobStorageEngine engine = new AzureBlobStorageEngine(properties);

        assertThat(engine.publicObjectUrl("nested/file.pdf"))
                .isEqualTo("https://account.blob.core.windows.net/container/base/nested/file.pdf");
        assertThat(engine.publicObjectUrl("base/nested/file.pdf"))
                .isEqualTo("https://account.blob.core.windows.net/container/base/nested/file.pdf");
    }

    @Test
    void publicObjectUrl_doesNotDuplicateContainerWhenEndpointAlreadyIncludesIt() {
        StorageProperties properties = new StorageProperties();
        ObjectConfig objectConfig = properties.getObject();
        objectConfig.setEndpoint("https://account.blob.core.windows.net/container");
        objectConfig.setBucketName("container");

        AzureBlobStorageEngine engine = new AzureBlobStorageEngine(properties);

        assertThat(engine.publicObjectUrl("file.txt"))
                .isEqualTo("https://account.blob.core.windows.net/container/file.txt");
    }

    @Test
    void uploadsDownloadsChecksAndReadsMetadataThroughAzureClient() throws Exception {
        when(blobClient.getProperties()).thenReturn(blobProperties);
        when(blobProperties.getVersionId()).thenReturn("version-1");
        when(blobProperties.getRequestId()).thenReturn("request-1");
        when(blobProperties.getContentMd5()).thenReturn("md5".getBytes());
        when(blobProperties.getBlobSize()).thenReturn(4L);
        when(blobProperties.getContentType()).thenReturn("text/plain");
        when(blobProperties.getContentEncoding()).thenReturn("utf-8");
        when(blobProperties.getETag()).thenReturn("etag");
        when(blobProperties.getMetadata()).thenReturn(java.util.Map.of("k", "v"));
        when(blobProperties.getLastModified()).thenReturn(java.time.OffsetDateTime.now());
        when(blobClient.downloadContent()).thenReturn(BinaryData.fromString("{\"a\":1}"));
        when(blobClient.exists()).thenReturn(true);

        var upload = engine.putObject("file.txt", new ByteArrayInputStream("data".getBytes()));
        assertThat(upload.isSuccess()).isTrue();
        assertThat(upload.getVersionId()).isEqualTo("version-1");
        assertThat(engine.getData("file.txt",
                new tools.jackson.core.type.TypeReference<java.util.Map<String, Integer>>() { }).getData())
                .containsEntry("a", 1);
        assertThat(engine.existsObject("file.txt")).isTrue();
        assertThat(engine.statObject("file.txt")).satisfies(stat -> {
            assertThat(stat.isExists()).isTrue();
            assertThat(stat.getContentLength()).isEqualTo(4L);
            assertThat(stat.getUserMetadata()).containsEntry("k", "v");
        });

        Path target = tempDir.resolve("download.txt");
        Files.createFile(target);
        var download = engine.getObject("file.txt", target.toFile(), false);
        assertThat(download.isSuccess()).isTrue();
        verify(blobClient).downloadToFile(target.toAbsolutePath().toString(), true);
    }

    @Test
    void handlesMissingObjectsAndSafeDirectPolicies() {
        when(blobClient.exists()).thenReturn(false);
        assertThat(engine.statObject("missing.txt").isExists()).isFalse();

        when(blobClient.generateSas(any(com.azure.storage.blob.sas.BlobServiceSasSignatureValues.class)))
                .thenReturn("sig");
        when(blobClient.getBlobUrl()).thenReturn("https://account/container/base/file.txt");
        assertThat(engine.issueDirectUploadPolicy("file.txt", 1).isFallback()).isFalse();
        assertThat(engine.issueDirectDownloadPolicy("file.txt", 1).isFallback()).isFalse();

        doThrow(new IllegalStateException("sas unavailable")).when(blobClient)
                .generateSas(any(com.azure.storage.blob.sas.BlobServiceSasSignatureValues.class));
        assertThat(engine.issueDirectUploadPolicy("file.txt", 1).isFallback()).isTrue();
        var failedDownload = engine.issueDirectDownloadPolicy("file.txt", 1);
        assertThat(failedDownload.isSuccess()).isFalse();
        assertThat(failedDownload.isFallback()).isFalse();
    }

    @Test
    void handlesFileUploadErrorsAndWritePermissionBranches() throws Exception {
        var missing = engine.putObject("missing.txt", tempDir.resolve("missing.txt").toFile());
        assertThat(missing.isSuccess()).isFalse();

        Path target = tempDir.resolve("missing-target.txt");
        AzureBlobStorageEngine second = new AzureBlobStorageEngine(properties);
        var response = second.getObject("file.txt", target.toFile(), true);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("permission");

        when(blobClient.getProperties()).thenReturn(blobProperties);
        when(blobProperties.getContentMd5()).thenReturn("md5".getBytes());
        assertThat(engine.putImage("file.txt", new ByteArrayInputStream("x".getBytes()), null)
                .isSuccess()).isTrue();
        assertThat(engine.getResumableObject("file.txt", target.toString(), false).isSuccess()).isFalse();
    }
}
