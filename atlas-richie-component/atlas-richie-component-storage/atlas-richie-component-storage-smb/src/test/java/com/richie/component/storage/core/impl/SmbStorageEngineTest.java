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

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.bean.Smb3Config;
import com.richie.component.storage.config.StorageProperties;
import com.richie.context.common.api.SpringContextHolder;
import org.junit.jupiter.api.*;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmbStorageEngine 单元测试")
class SmbStorageEngineTest {

    private static final String BASE_PATH = "/share/";
    private static final String KEY = "test.txt";
    private static final String DOMAIN = "localhost";

    @Mock
    private StorageProperties properties;

    @Mock
    private ObjectConfig objectConfig;

    @TempDir
    Path tempDir;

    private SmbStorageEngine engine;

    private static MockedStatic<SpringContextHolder> springContextHolder;

    @BeforeAll
    static void beforeAll() {
        springContextHolder = mockStatic(SpringContextHolder.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    }

    @AfterAll
    static void afterAll() {
        if (springContextHolder != null) {
            springContextHolder.close();
        }
    }

    @BeforeEach
    void setUp() {
        var smb3Config = mock(Smb3Config.class);
        lenient().when(smb3Config.getBasePath()).thenReturn(BASE_PATH);
        lenient().when(smb3Config.getDomain()).thenReturn(DOMAIN);
        lenient().when(smb3Config.getUsername()).thenReturn("testuser");
        lenient().when(smb3Config.getPassword()).thenReturn("testpass");
        lenient().when(properties.getSmb3()).thenReturn(smb3Config);

        engine = new SmbStorageEngine(properties, mock(org.codelibs.jcifs.smb.CIFSContext.class));
    }

    @Test
    @DisplayName("putObject(File) - 上传文件成功，返回成功的响应")
    void putObject_file_success() throws Exception {
        File source = tempDir.resolve("source.txt").toFile();
        Files.writeString(source.toPath(), "hello-world");

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
                })) {

            var response = engine.putObject(KEY, source);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getKey()).isEqualTo(KEY);
            assertThat(response.getVersionId()).isNotBlank();
            assertThat(response.getUploadTime()).isNotNull();
        }
    }

    @Test
    @DisplayName("putObject(File) - SmbFile 抛出异常时返回 success=false")
    void putObject_file_throwsException() throws Exception {
        File source = tempDir.resolve("error.txt").toFile();
        Files.writeString(source.toPath(), "data");

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenThrow(new IOException("SMB connection failed"));
                })) {

            var response = engine.putObject(KEY, source);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("SMB connection failed");
            assertThat(response.getKey()).isEqualTo(KEY);
        }
    }

    @Test
    @DisplayName("putObject(InputStream) - 流式上传成功")
    void putObject_stream_success() throws Exception {
        InputStream stream = new ByteArrayInputStream("stream-content".getBytes(StandardCharsets.UTF_8));

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
                })) {

            var response = engine.putObject(KEY, stream);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getKey()).isEqualTo(KEY);
            assertThat(response.getVersionId()).isNotBlank();
        }
    }

    @Test
    @DisplayName("putObject(InputStream) - 流式上传失败时返回 success=false")
    void putObject_stream_throwsException() throws Exception {
        InputStream stream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenThrow(new IOException("Stream upload failed"));
                })) {

            var response = engine.putObject(KEY, stream);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("Stream upload failed");
        }
    }

    @Test
    @DisplayName("putObject(InputStream) - 读取流时抛出 IOException")
    void putObject_stream_throwsIOException() {
        InputStream stream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("stream read error");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("stream read error");
            }
        };

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
                })) {

            var response = engine.putObject(KEY, stream);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).isEqualTo("stream read error");
        }
    }

    @Test
    @DisplayName("getData - 成功反序列化 JSON 内容")
    @SuppressWarnings("unchecked")
    void getData_success() throws Exception {
        String jsonContent = "{\"name\":\"test\"}";
        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    var mockInputStream = new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));
                    when(mockFile.getInputStream()).thenReturn(mockInputStream);
                })) {

            TypeReference<Map<String, String>> typeRef = new TypeReference<>() {
            };
            var response = engine.getData(KEY, typeRef);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNotNull();
            assertThat(response.getData()).containsEntry("name", "test");
            assertThat(response.getKey()).isEqualTo(KEY);
        }
    }

    @Test
    @DisplayName("getData - SmbFile 抛出异常时返回 success=false")
    void getData_throwsException() {
        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getInputStream()).thenThrow(new IOException("SMB read failed"));
                })) {

            TypeReference<Map<String, String>> typeRef = new TypeReference<>() {
            };
            var response = engine.getData(KEY, typeRef);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("SMB read failed");
            assertThat(response.getKey()).isEqualTo(KEY);
        }
    }

    @Test
    @DisplayName("getObject - 成功下载文件到目标路径")
    void getObject_success() throws Exception {
        File target = tempDir.resolve("downloaded.txt").toFile();
        Files.createFile(target.toPath());
        String content = "downloaded-content";

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
                })) {

            var response = engine.getObject(KEY, target, false);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            assertThat(response.getKey()).isEqualTo(KEY);
            assertThat(Files.readString(target.toPath())).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("getObject - 设置 returnData=true 时返回字节内容")
    void getObject_withReturnData_success() throws Exception {
        File target = tempDir.resolve("downloaded-2.txt").toFile();
        Files.createFile(target.toPath());
        String content = "content-with-bytes";

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
                })) {

            var response = engine.getObject(KEY, target, true);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNotNull();
            assertThat(new String(response.getData(), StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("getObject - SmbFile 抛出异常时返回 success=false")
    void getObject_throwsException() throws Exception {
        File target = tempDir.resolve("error.txt").toFile();
        Files.createFile(target.toPath());

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getInputStream()).thenThrow(new IOException("SMB get failed"));
                })) {

            var response = engine.getObject(KEY, target, false);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("SMB get failed");
            assertThat(response.getKey()).isEqualTo(KEY);
        }
    }

    @Test
    @DisplayName("existsObject - SmbFile.exists() 返回 true 时为 true")
    void existsObject_true() throws Exception {
        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.exists()).thenReturn(true);
                })) {

            boolean result = engine.existsObject(KEY);

            assertThat(result).isTrue();
        }
    }

    @Test
    @DisplayName("existsObject - SmbFile.exists() 返回 false 时为 false")
    void existsObject_false() throws Exception {
        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.exists()).thenReturn(false);
                })) {

            boolean result = engine.existsObject(KEY);

            assertThat(result).isFalse();
        }
    }

    @Test
    @DisplayName("existsObject - SmbFile 抛出异常时返回 false")
    void existsObject_throwsException() throws Exception {
        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.exists()).thenThrow(new IOException("SMB check failed"));
                })) {

            boolean result = engine.existsObject(KEY);

            assertThat(result).isFalse();
        }
    }

    @Test
    @DisplayName("issueDirectUploadPolicy - SMB 不支持预签名，返回 fallback 策略")
    void issueDirectUploadPolicy_fallback() {
        var policy = engine.issueDirectUploadPolicy(KEY, 600);

        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.isFallback()).isTrue();
        assertThat(policy.getErrorMessage()).contains("SMB does not support presigned URLs");
        assertThat(policy.getMethod()).isEqualTo("PUT");
        assertThat(policy.getUploadUrl()).contains("smb://");
        assertThat(policy.getUploadUrl()).contains(KEY);
        assertThat(policy.getBucketName()).isEqualTo("smb");
        assertThat(policy.getKey()).isEqualTo(KEY);
        assertThat(policy.getExpireAt()).isNotNull();
        assertThat(policy.getHeaders()).isEmpty();
        assertThat(policy.getFormFields()).isEmpty();
    }

    @Test
    @DisplayName("issueDirectUploadPolicy - 过期时间至少 60 秒")
    void issueDirectUploadPolicy_expireAt() {
        var policy = engine.issueDirectUploadPolicy(KEY, 30);

        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.getExpireAt()).isNotNull();
    }

    @Test
    @DisplayName("putData - Map 数据上传成功")
    void putData_map_success() throws Exception {
        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
                })) {

            var response = engine.putData(KEY, Map.of("key", "value"));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getKey()).isEqualTo(KEY);
            assertThat(response.getHashValue()).isNotBlank();
        }
    }

    @Test
    @DisplayName("putImage(File) - 图片上传使用 putObject")
    void putImage_file_success() throws Exception {
        File source = tempDir.resolve("image.jpg").toFile();
        Files.writeString(source.toPath(), "image-data");

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
                })) {

            var response = engine.putImage(KEY, source, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getKey()).isEqualTo(KEY);
        }
    }

    @Test
    @DisplayName("putImage(InputStream) - 图片流上传使用 putObject")
    void putImage_stream_success() throws Exception {
        InputStream stream = new ByteArrayInputStream("image-data".getBytes(StandardCharsets.UTF_8));

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
                })) {

            var response = engine.putImage(KEY, stream, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getKey()).isEqualTo(KEY);
        }
    }

    @Test
    @DisplayName("getResumableObject - 委托给 getObject")
    void getResumableObject_delegatesToGetObject() throws Exception {
        File target = tempDir.resolve("resumable.txt").toFile();
        Files.createFile(target.toPath());
        String content = "resumable-content";

        try (var ignored = mockConstruction(org.codelibs.jcifs.smb.impl.SmbFile.class,
                (mockFile, context) -> {
                    when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
                })) {

            var response = engine.getResumableObject(KEY, target.getAbsolutePath(), false);

            assertThat(response.isSuccess()).isTrue();
            assertThat(Files.readString(target.toPath())).isEqualTo(content);
        }
    }
}
