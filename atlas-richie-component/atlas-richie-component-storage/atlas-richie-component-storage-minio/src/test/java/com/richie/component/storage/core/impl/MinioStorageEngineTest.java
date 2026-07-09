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

import com.richie.component.storage.bean.DirectUploadPolicy;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.bean.image.ImageOptions;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.MinioAclTypeConverter;
import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.context.common.api.SpringContextHolder;
import io.minio.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinioStorageEngineTest {

    private static MockedStatic<SpringContextHolder> springContextHolderMock;

    @TempDir
    Path tempDir;

    @Mock
    private MinioAsyncClient minioAsyncClient;

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private ObjectConfig objectConfig;

    private MinioStorageEngine engine;

    @BeforeAll
    static void openMockStatic() {
        springContextHolderMock = mockStatic(SpringContextHolder.class);
    }

    @AfterAll
    static void closeMockStatic() {
        if (springContextHolderMock != null) {
            springContextHolderMock.close();
            springContextHolderMock = null;
        }
    }

    @BeforeEach
    void setUp() {
        springContextHolderMock.when(() -> SpringContextHolder.getBean(MinioAsyncClient.class)).thenReturn(minioAsyncClient);
        springContextHolderMock.when(() -> SpringContextHolder.getBean((Class<MinioAsyncClient>) any())).thenReturn(minioAsyncClient);
        when(storageProperties.getObject()).thenReturn(objectConfig);
        when(objectConfig.getBasePath()).thenReturn("base");
        when(objectConfig.getBucketName()).thenReturn("test-bucket");
        when(objectConfig.getEndpoint()).thenReturn("play.min.io");
        when(objectConfig.getAcl()).thenReturn(null);

        engine = new MinioStorageEngine(storageProperties, minioAsyncClient);
    }

    // ==================== putObject(File) tests ====================

    @Test
    void putObject_file_success() throws Exception {
        Path tempFile = Files.createTempFile(tempDir, "test", ".txt");
        Files.writeString(tempFile, "test content");
        File file = tempFile.toFile();

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"etag123\"");
        when(writeResponse.versionId()).thenReturn("version1");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("test.txt", file);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"etag123\"");
        assertThat(response.getVersionId()).isEqualTo("version1");
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
        assertThat(response.getUrl()).contains("test-bucket");
        assertThat(response.getUrl()).contains("base/test.txt");
    }

    @Test
    void putObject_file_failure_executionException() throws Exception {
        Path tempFile = Files.createTempFile(tempDir, "test", ".txt");
        Files.writeString(tempFile, "test content");
        File file = tempFile.toFile();

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.failedFuture(new RuntimeException("Connection failed"));
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("test.txt", file);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Connection failed");
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
        assertThat(response.getKey()).contains("base/test.txt");
    }

    // ==================== putObject(InputStream) tests ====================

    @Test
    void putObject_inputStream_success() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"etag456\"");
        when(writeResponse.versionId()).thenReturn("version2");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("stream.txt", inputStream);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"etag456\"");
        assertThat(response.getVersionId()).isEqualTo("version2");
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
    }

    @Test
    void putObject_inputStream_failure_executionException() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.failedFuture(new RuntimeException("Stream error"));
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("stream.txt", inputStream);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Stream error");
    }

    // ==================== putObject with ACL tests ====================

    @Test
    void putObject_file_withAcl_success() throws Exception {
        when(objectConfig.getAcl()).thenReturn(AclTypeEnum.PUBLIC_READ);
        ReflectionTestUtils.setField(engine, "aclTypeConverter", new MinioAclTypeConverter());

        Path tempFile = Files.createTempFile(tempDir, "acl", ".txt");
        Files.writeString(tempFile, "acl content");
        File file = tempFile.toFile();

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"acl-etag\"");
        when(writeResponse.versionId()).thenReturn("acl-version");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("acl-file.txt", file);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"acl-etag\"");
    }

    @Test
    void putObject_inputStream_withAcl_success() throws Exception {
        when(objectConfig.getAcl()).thenReturn(AclTypeEnum.PUBLIC_READ);
        ReflectionTestUtils.setField(engine, "aclTypeConverter", new MinioAclTypeConverter());

        ByteArrayInputStream inputStream = new ByteArrayInputStream("acl content".getBytes());

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"acl-stream-etag\"");
        when(writeResponse.versionId()).thenReturn("acl-stream-version");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("acl-stream.txt", inputStream);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"acl-stream-etag\"");
    }

    // ==================== putImage(File,ImageOptions) tests ====================

    @Test
    void putImage_file_success() throws Exception {
        Path tempFile = Files.createTempFile(tempDir, "test", ".png");
        Files.writeString(tempFile, "image content");
        File file = tempFile.toFile();

        ImageOptions options = new ImageOptions().setScale(50);

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"img-etag\"");
        when(writeResponse.versionId()).thenReturn("img-version");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putImage("image.png", file, options);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"img-etag\"");
    }

    @Test
    void putImage_file_failure() throws Exception {
        Path tempFile = Files.createTempFile(tempDir, "test", ".png");
        Files.writeString(tempFile, "image content");
        File file = tempFile.toFile();

        ImageOptions options = new ImageOptions().setScale(50);

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.failedFuture(new RuntimeException("Image upload failed"));
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putImage("image.png", file, options);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Image upload failed");
    }

    // ==================== putImage(InputStream,ImageOptions) tests ====================

    @Test
    void putImage_inputStream_success() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("image data".getBytes());
        ImageOptions options = new ImageOptions().setQuality(90);

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"stream-img-etag\"");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putImage("stream-img.png", inputStream, options);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"stream-img-etag\"");
    }

    @Test
    void putImage_inputStream_failure() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("image data".getBytes());
        ImageOptions options = new ImageOptions().setQuality(90);

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.failedFuture(new RuntimeException("Stream image failed"));
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putImage("stream-img.png", inputStream, options);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Stream image failed");
    }

    // ==================== getData tests ====================

    @Test
    void getData_success() throws Exception {
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        byte[] jsonBytes = "{\"name\":\"test\"}".getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(jsonBytes);
        when(getObjectResponse.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> {
            byte[] buffer = invocation.getArgument(0);
            int offset = invocation.getArgument(1);
            int len = invocation.getArgument(2);
            return bais.read(buffer, offset, len);
        });
        when(getObjectResponse.read(any(byte[].class))).thenAnswer(invocation -> {
            byte[] buffer = invocation.getArgument(0);
            return bais.read(buffer);
        });

        CompletableFuture<GetObjectResponse> future = CompletableFuture.completedFuture(getObjectResponse);
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getData("data.json", new TypeReference<Map<String, String>>() {});

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
        assertThat(response.getKey()).contains("base/data.json");
        assertThat(response.getData()).isNotNull();
    }

    @Test
    void getData_failure_getObjectThrowsException() throws Exception {
        CompletableFuture<GetObjectResponse> future = CompletableFuture.failedFuture(new RuntimeException("Object not found"));
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getData("missing.json", new TypeReference<Map<String, String>>() {});

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Object not found");
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
        assertThat(response.getKey()).contains("base/missing.json");
    }

    @Test
    void getData_failure_readThrowsException() throws Exception {
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(getObjectResponse.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new RuntimeException("Read error"));

        CompletableFuture<GetObjectResponse> future = CompletableFuture.completedFuture(getObjectResponse);
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getData("corrupt.json", new TypeReference<Map<String, String>>() {});

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Read error");
    }

    // ==================== getObject tests ====================

    @Test
    void getObject_returnDataTrue_success() throws Exception {
        Path targetFile = Files.createTempFile(tempDir, "download", ".txt");
        File targetPath = targetFile.toFile();
        Files.writeString(targetFile, "downloaded content");

        ByteArrayInputStream bais = new ByteArrayInputStream("downloaded content".getBytes());
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(getObjectResponse.read(any(byte[].class))).thenAnswer(invocation -> bais.read(invocation.getArgument(0)));

        CompletableFuture<GetObjectResponse> future = CompletableFuture.completedFuture(getObjectResponse);
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getObject("file.txt", targetPath, true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
        assertThat(response.getData()).isNotNull();
        assertThat(new String(response.getData())).isEqualTo("downloaded content");
    }

    @Test
    void getObject_returnDataFalse_success() throws Exception {
        Path targetFile = Files.createTempFile(tempDir, "download", ".txt");
        File targetPath = targetFile.toFile();

        ByteArrayInputStream bais = new ByteArrayInputStream("some content".getBytes());
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(getObjectResponse.read(any(byte[].class))).thenAnswer(invocation -> bais.read(invocation.getArgument(0)));

        CompletableFuture<GetObjectResponse> future = CompletableFuture.completedFuture(getObjectResponse);
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getObject("file.txt", targetPath, false);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
        assertThat(response.getData()).isNull();
    }

    @Test
    void getObject_nonWritableDirectory() throws Exception {
        File nonWritableDir = mock(File.class);
        when(nonWritableDir.canWrite()).thenReturn(false);

        var response = engine.getObject("file.txt", nonWritableDir, true);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("permission");
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
    }

    @Test
    void getObject_failure_getObjectThrowsException() throws Exception {
        Path targetFile = Files.createTempFile(tempDir, "download", ".txt");
        File targetPath = targetFile.toFile();

        CompletableFuture<GetObjectResponse> future = CompletableFuture.failedFuture(new RuntimeException("Get object failed"));
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getObject("missing.txt", targetPath, true);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Get object failed");
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
    }

    @Test
    void getObject_failure_writeToFileThrowsException() throws Exception {
        Path targetFile = Files.createTempFile(tempDir, "download", ".txt");
        File targetPath = targetFile.toFile();

        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(getObjectResponse.read(any(byte[].class))).thenThrow(new RuntimeException("Write error"));

        CompletableFuture<GetObjectResponse> future = CompletableFuture.completedFuture(getObjectResponse);
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getObject("file.txt", targetPath, true);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Write error");
    }

    // ==================== getResumableObject tests ====================

    @Test
    void getResumableObject_success() throws Exception {
        Path targetFile = Files.createTempFile(tempDir, "resumable", ".txt");
        String targetPath = targetFile.toString();

        ByteArrayInputStream bais = new ByteArrayInputStream("resumable content".getBytes());
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(getObjectResponse.read(any(byte[].class))).thenAnswer(invocation -> bais.read(invocation.getArgument(0)));

        CompletableFuture<GetObjectResponse> future = CompletableFuture.completedFuture(getObjectResponse);
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getResumableObject("resume.txt", targetPath, true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo("test-bucket");
    }

    @Test
    void getResumableObject_delegatesToGetObject() throws Exception {
        Path targetFile = Files.createTempFile(tempDir, "delegate", ".txt");
        String targetPath = targetFile.toString();

        ByteArrayInputStream bais = new ByteArrayInputStream("delegated content".getBytes());
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(getObjectResponse.read(any(byte[].class))).thenAnswer(invocation -> bais.read(invocation.getArgument(0)));

        CompletableFuture<GetObjectResponse> future = CompletableFuture.completedFuture(getObjectResponse);
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getResumableObject("delegate.txt", targetPath, false);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void getResumableObject_failure() throws Exception {
        Path targetFile = Files.createTempFile(tempDir, "resumable", ".txt");
        String targetPath = targetFile.toString();

        CompletableFuture<GetObjectResponse> future = CompletableFuture.failedFuture(new RuntimeException("Resumable failed"));
        when(minioAsyncClient.getObject(any(GetObjectArgs.class))).thenReturn(future);

        var response = engine.getResumableObject("missing.txt", targetPath, true);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Resumable failed");
    }

    // ==================== existsObject tests ====================

    @Test
    void existsObject_true() throws Exception {
        StatObjectResponse statResponse = mock(StatObjectResponse.class);
        CompletableFuture<StatObjectResponse> future = CompletableFuture.completedFuture(statResponse);
        when(minioAsyncClient.statObject(any(StatObjectArgs.class))).thenReturn(future);

        boolean exists = engine.existsObject("existing.txt");

        assertThat(exists).isTrue();
    }

    @Test
    void existsObject_false_statObjectThrowsException() throws Exception {
        CompletableFuture<StatObjectResponse> future = CompletableFuture.failedFuture(new RuntimeException("Object not found"));
        when(minioAsyncClient.statObject(any(StatObjectArgs.class))).thenReturn(future);

        boolean exists = engine.existsObject("missing.txt");

        assertThat(exists).isFalse();
    }

    // ==================== issueDirectUploadPolicy tests ====================

    @Test
    void issueDirectUploadPolicy_success() throws Exception {
        String presignedUrl = "https://play.min.io/test-bucket/base/file.txt?signature=abc";
        when(minioAsyncClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(presignedUrl);

        DirectUploadPolicy policy = engine.issueDirectUploadPolicy("file.txt", 3600);

        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.getUploadUrl()).isEqualTo(presignedUrl);
        assertThat(policy.getMethod()).isEqualTo("PUT");
        assertThat(policy.getBucketName()).isEqualTo("test-bucket");
        assertThat(policy.getKey()).contains("base/file.txt");
        assertThat(policy.isFallback()).isFalse();
    }

    @Test
    void issueDirectUploadPolicy_failure_fallbackPolicy() throws Exception {
        when(minioAsyncClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenThrow(new RuntimeException("Presigned URL failed"));

        DirectUploadPolicy policy = engine.issueDirectUploadPolicy("file.txt", 3600);

        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.getErrorMessage()).contains("兜底直传链接");
        assertThat(policy.getMethod()).isEqualTo("PUT");
        assertThat(policy.getBucketName()).isEqualTo("test-bucket");
        assertThat(policy.getKey()).contains("base/file.txt");
        assertThat(policy.isFallback()).isTrue();
    }

    @Test
    void issueDirectUploadPolicy_expireSecondsBelowMinimum() throws Exception {
        String presignedUrl = "https://play.min.io/test-bucket/base/file.txt?signature=xyz";
        when(minioAsyncClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(presignedUrl);

        DirectUploadPolicy policy = engine.issueDirectUploadPolicy("file.txt", 30);

        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.getUploadUrl()).isEqualTo(presignedUrl);
    }

    // ==================== putImage tests (delegation verification) ====================

    @Test
    void putImage_file_delegatesToPutObject() throws Exception {
        Path tempFile = Files.createTempFile(tempDir, "test", ".png");
        Files.writeString(tempFile, "png content");
        File file = tempFile.toFile();

        ImageOptions options = new ImageOptions();

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"png-etag\"");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putImage("test.png", file, options);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"png-etag\"");
    }

    @Test
    void putImage_inputStream_delegatesToPutObject() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("png stream".getBytes());
        ImageOptions options = new ImageOptions();

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("\"png-stream-etag\"");

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.completedFuture(writeResponse);
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putImage("stream.png", inputStream, options);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("\"png-stream-etag\"");
    }

    // ==================== getBucketName and objectConfig tests ====================

    @Test
    void getBucketName_returnsConfiguredBucketName() {
        assertThat(engine.getBucketName()).isEqualTo("test-bucket");
    }

    @Test
    void objectConfig_returnsConfiguredObjectConfig() {
        assertThat(engine.objectConfig()).isEqualTo(objectConfig);
    }

    @Test
    void getRealPath_prependsBasePath() {
        assertThat(engine.getResourceKey("test.txt")).contains("play.min.io");
    }

    // ==================== putObject exception handling tests ====================

    @Test
    void putObject_inputStream_failure_interruptedException() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.failedFuture(new InterruptedException("Interrupted"));
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("stream.txt", inputStream);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Interrupted");
    }

    @Test
    void putObject_file_failure_nullPointerException() throws Exception {
        Path tempFile = Files.createTempFile(tempDir, "test", ".txt");
        Files.writeString(tempFile, "test content");
        File file = tempFile.toFile();

        CompletableFuture<ObjectWriteResponse> future = CompletableFuture.failedFuture(new NullPointerException("Null value"));
        when(minioAsyncClient.putObject(any(PutObjectArgs.class))).thenReturn(future);

        UploadResponse response = engine.putObject("test.txt", file);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Null value");
    }
}
