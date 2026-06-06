package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.AclTypeConverter;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.context.common.api.SpringContextHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("S3StorageEngine 单元测试")
class S3StorageEngineTest {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String BASE_PATH = "base";
    private static final String KEY = "test.txt";
    private static final String ENDPOINT = "s3.amazonaws.com";

    @Mock
    private StorageProperties properties;

    @Mock
    private ObjectConfig objectConfig;

    @Mock
    private StorageTypeConverter storageTypeConverter;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private AclTypeConverter<ObjectCannedACL> aclConverter;

    @TempDir
    Path tempDir;

    private S3StorageEngine engine;

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
        engine = new S3StorageEngine(properties, storageTypeConverter);
        ReflectionTestUtils.setField(engine, "s3Presigner", s3Presigner);
        ReflectionTestUtils.setField(engine, "aclTypeConverter", aclConverter);

        lenient().when(properties.getObject()).thenReturn(objectConfig);
        lenient().when(objectConfig.getBucketName()).thenReturn(BUCKET_NAME);
        lenient().when(objectConfig.getBasePath()).thenReturn(BASE_PATH);
        lenient().when(objectConfig.getEndpoint()).thenReturn(ENDPOINT);
        lenient().when(objectConfig.getAcl()).thenReturn(null);
        lenient().when(objectConfig.getStorageType()).thenReturn(null);
        lenient().when(storageTypeConverter.convertToEngineType(any())).thenReturn(null);

        springContextHolder.when(() -> SpringContextHolder.getBean(S3Client.class)).thenReturn(s3Client);
    }

    private PutObjectResponse buildPutResponse() {
        PutObjectResponse putResponse = mock(PutObjectResponse.class);
        lenient().when(putResponse.versionId()).thenReturn("version-123");
        lenient().when(putResponse.eTag()).thenReturn("etag-abc");
        return putResponse;
    }

    private ResponseInputStream<GetObjectResponse> buildResponseStream(String content) {
        GetObjectResponse response = mock(GetObjectResponse.class);
        lenient().when(response.eTag()).thenReturn("etag-123");
        lenient().when(response.versionId()).thenReturn("version-123");
        return new ResponseInputStream<>(response,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("putObject(File) - 上传文件成功，返回包含版本号与E-Tag的响应")
    void putObject_file_success() throws Exception {
        File source = tempDir.resolve("source.txt").toFile();
        Files.writeString(source.toPath(), "hello-world");
        PutObjectResponse putResponse = buildPutResponse();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(putResponse);

        var response = engine.putObject(KEY, source);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getVersionId()).isEqualTo("version-123");
        assertThat(response.getRequestId()).isEqualTo("etag-abc");
        assertThat(response.getHashValue()).isEqualTo("etag-abc");
        assertThat(response.getUrl()).isEqualTo("https://test-bucket.s3.amazonaws.com/base/test.txt");
        assertThat(response.getUploadTime()).isNotNull();
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("putObject(File) - 携带 ACL 时写入 ObjectCannedACL")
    void putObject_file_withAcl_success() throws Exception {
        File source = tempDir.resolve("acl.txt").toFile();
        Files.writeString(source.toPath(), "acl-data");
        when(objectConfig.getAcl()).thenReturn(AclTypeEnum.PUBLIC_READ);
        when(aclConverter.convertToEngineAcl(AclTypeEnum.PUBLIC_READ)).thenReturn(ObjectCannedACL.PUBLIC_READ);
        PutObjectResponse putResponse = buildPutResponse();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(putResponse);

        var response = engine.putObject(KEY, source);

        assertThat(response.isSuccess()).isTrue();
        verify(aclConverter).convertToEngineAcl(AclTypeEnum.PUBLIC_READ);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("putObject(File) - 携带存储类型时设置 StorageClass")
    void putObject_file_withStorageClass_success() throws Exception {
        File source = tempDir.resolve("sc.txt").toFile();
        Files.writeString(source.toPath(), "sc-data");
        when(objectConfig.getStorageType()).thenReturn(StorageTypeEnum.STANDARD);
        when(storageTypeConverter.convertToEngineType(StorageTypeEnum.STANDARD)).thenReturn("STANDARD");
        PutObjectResponse putResponse = buildPutResponse();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(putResponse);

        var response = engine.putObject(KEY, source);

        assertThat(response.isSuccess()).isTrue();
        verify(storageTypeConverter).convertToEngineType(StorageTypeEnum.STANDARD);
    }

    @Test
    @DisplayName("putObject(File) - AWS 服务异常时返回 success=false")
    void putObject_file_throwsAwsServiceException() throws Exception {
        File source = tempDir.resolve("err.txt").toFile();
        Files.writeString(source.toPath(), "data");
        AwsServiceException awsEx = mock(AwsServiceException.class);
        lenient().when(awsEx.getMessage()).thenReturn("aws service error");
        lenient().when(awsEx.requestId()).thenReturn("aws-req-1");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(awsEx);

        var response = engine.putObject(KEY, source);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("aws service error");
        assertThat(response.getRequestId()).isEqualTo("aws-req-1");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getKey()).isEqualTo("base/test.txt");
    }

    @Test
    @DisplayName("putObject(File) - SdkException 异常时返回 success=false")
    void putObject_file_throwsSdkException() throws Exception {
        File source = tempDir.resolve("sdk.txt").toFile();
        Files.writeString(source.toPath(), "data");
        SdkException sdkEx = mock(SdkException.class);
        lenient().when(sdkEx.getMessage()).thenReturn("sdk error");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(sdkEx);

        var response = engine.putObject(KEY, source);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("sdk error");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getKey()).isEqualTo("base/test.txt");
    }

    @Test
    @DisplayName("putObject(InputStream) - 流式上传成功")
    void putObject_stream_success() {
        InputStream stream = new ByteArrayInputStream("stream-content".getBytes(StandardCharsets.UTF_8));
        PutObjectResponse putResponse = buildPutResponse();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(putResponse);

        var response = engine.putObject(KEY, stream);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getVersionId()).isEqualTo("version-123");
        assertThat(response.getRequestId()).isEqualTo("etag-abc");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("putObject(InputStream) - AWS 服务异常时返回 success=false")
    void putObject_stream_throwsAwsServiceException() {
        InputStream stream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
        AwsServiceException awsEx = mock(AwsServiceException.class);
        lenient().when(awsEx.getMessage()).thenReturn("stream aws error");
        lenient().when(awsEx.requestId()).thenReturn("stream-req-1");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(awsEx);

        var response = engine.putObject(KEY, stream);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("stream aws error");
        assertThat(response.getRequestId()).isEqualTo("stream-req-1");
    }

    @Test
    @DisplayName("putObject(InputStream) - SdkException 异常时返回 success=false")
    void putObject_stream_throwsSdkException() {
        InputStream stream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
        SdkException sdkEx = mock(SdkException.class);
        lenient().when(sdkEx.getMessage()).thenReturn("stream sdk error");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(sdkEx);

        var response = engine.putObject(KEY, stream);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("stream sdk error");
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

        var response = engine.putObject(KEY, stream);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("stream read error");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
    }

    @Test
    @DisplayName("getData - 成功反序列化 JSON 内容")
    @SuppressWarnings("unchecked")
    void getData_success() {
        ResponseInputStream<GetObjectResponse> responseStream = buildResponseStream("{\"name\":\"test\"}");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        TypeReference<Map<String, String>> typeRef = new TypeReference<>() {
        };
        var response = engine.getData(KEY, typeRef);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData()).containsEntry("name", "test");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getRequestId()).isEqualTo("etag-123");
        assertThat(response.getVersionId()).isEqualTo("version-123");
        assertThat(response.getKey()).isEqualTo("base/test.txt");
    }

    @Test
    @DisplayName("getData - 读取流时抛出 IOException")
    void getData_throwsIOException() {
        InputStream throwingStream = new InputStream() {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("read failure");
            }

            @Override
            public int read() throws IOException {
                throw new IOException("read failure");
            }
        };
        GetObjectResponse getResponse = mock(GetObjectResponse.class);
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(getResponse, throwingStream);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        TypeReference<Map<String, String>> typeRef = new TypeReference<>() {
        };
        var response = engine.getData(KEY, typeRef);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("read failure");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
    }

    @Test
    @DisplayName("getData - AWS 服务异常时返回 success=false")
    void getData_throwsAwsServiceException() {
        AwsServiceException awsEx = mock(AwsServiceException.class);
        lenient().when(awsEx.getMessage()).thenReturn("aws data error");
        lenient().when(awsEx.requestId()).thenReturn("data-req-1");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(awsEx);

        TypeReference<Map<String, String>> typeRef = new TypeReference<>() {
        };
        var response = engine.getData(KEY, typeRef);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("aws data error");
        assertThat(response.getRequestId()).isEqualTo("data-req-1");
    }

    @Test
    @DisplayName("getData - SdkException 异常时返回 success=false")
    void getData_throwsSdkException() {
        SdkException sdkEx = mock(SdkException.class);
        lenient().when(sdkEx.getMessage()).thenReturn("sdk data error");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(sdkEx);

        TypeReference<Map<String, String>> typeRef = new TypeReference<>() {
        };
        var response = engine.getData(KEY, typeRef);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("sdk data error");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
    }

    @Test
    @DisplayName("getObject - 目标路径不可写时返回错误")
    void getObject_cannotWrite() {
        File nonWritable = new File("/non/existent/path/that/cannot/be/written/file.bin");

        var response = engine.getObject(KEY, nonWritable, false);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("does not have permission to write");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getKey()).isEqualTo("base/test.txt");
    }

    @Test
    @DisplayName("getObject - 不返回字节内容的成功下载")
    void getObject_withoutReturnData_success() throws Exception {
        File target = tempDir.resolve("downloaded-1.bin").toFile();
        Files.createFile(target.toPath());
        ResponseInputStream<GetObjectResponse> responseStream = buildResponseStream("download-data");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        var response = engine.getObject(KEY, target, false);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getRequestId()).isEqualTo("etag-123");
        assertThat(response.getVersionId()).isEqualTo("version-123");
        assertThat(target).exists();
        assertThat(Files.readString(target.toPath())).isEqualTo("download-data");
    }

    @Test
    @DisplayName("getObject - 返回字节内容的成功下载")
    void getObject_withReturnData_success() throws Exception {
        File target = tempDir.resolve("downloaded-2.bin").toFile();
        Files.createFile(target.toPath());
        ResponseInputStream<GetObjectResponse> responseStream = buildResponseStream("download-data-with-bytes");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        var response = engine.getObject(KEY, target, true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(new String(response.getData(), StandardCharsets.UTF_8))
                .isEqualTo("download-data-with-bytes");
        assertThat(response.getRequestId()).isEqualTo("etag-123");
    }

    @Test
    @DisplayName("getObject - AWS 服务异常时返回 success=false")
    void getObject_throwsAwsServiceException() throws Exception {
        File target = tempDir.resolve("aws-err.bin").toFile();
        Files.createFile(target.toPath());
        AwsServiceException awsEx = mock(AwsServiceException.class);
        lenient().when(awsEx.getMessage()).thenReturn("aws object error");
        lenient().when(awsEx.requestId()).thenReturn("obj-req-1");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(awsEx);

        var response = engine.getObject(KEY, target, false);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("aws object error");
        assertThat(response.getRequestId()).isEqualTo("obj-req-1");
    }

    @Test
    @DisplayName("getObject - SdkException 异常时返回 success=false")
    void getObject_throwsSdkException() throws Exception {
        File target = tempDir.resolve("sdk-err.bin").toFile();
        Files.createFile(target.toPath());
        SdkException sdkEx = mock(SdkException.class);
        lenient().when(sdkEx.getMessage()).thenReturn("sdk object error");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(sdkEx);

        var response = engine.getObject(KEY, target, false);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("sdk object error");
    }

    @Test
    @DisplayName("getResumableObject - 目录不可写时返回错误")
    void getResumableObject_cannotWrite() {
        String targetPath = "/non/existent/resumable/path/file.bin";

        var response = engine.getResumableObject(KEY, targetPath, false);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("does not have permission to write");
        assertThat(response.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getKey()).isEqualTo("base/test.txt");
    }

    @Test
    @DisplayName("getResumableObject - 不返回字节的成功下载")
    void getResumableObject_success() throws Exception {
        File target = tempDir.resolve("resumable-1.bin").toFile();
        Files.createFile(target.toPath());
        ResponseInputStream<GetObjectResponse> responseStream = buildResponseStream("resumable-content");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        var response = engine.getResumableObject(KEY, target.getAbsolutePath(), false);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getRequestId()).isEqualTo("etag-123");
        assertThat(response.getVersionId()).isEqualTo("version-123");
        assertThat(target).exists();
        assertThat(Files.readString(target.toPath())).isEqualTo("resumable-content");
    }

    @Test
    @DisplayName("getResumableObject - 返回字节的成功下载")
    void getResumableObject_withReturnData_success() throws Exception {
        File target = tempDir.resolve("resumable-2.bin").toFile();
        Files.createFile(target.toPath());
        ResponseInputStream<GetObjectResponse> responseStream = buildResponseStream("resumable-with-data");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        var response = engine.getResumableObject(KEY, target.getAbsolutePath(), true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(new String(response.getData(), StandardCharsets.UTF_8))
                .isEqualTo("resumable-with-data");
    }

    @Test
    @DisplayName("existsObject - headObject 正常返回时为 true")
    void existsObject_true() {
        HeadObjectResponse headResponse = mock(HeadObjectResponse.class);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headResponse);

        boolean result = engine.existsObject(KEY);

        assertThat(result).isTrue();
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    @DisplayName("existsObject - headObject 抛出 NoSuchKeyException 时为 false")
    void existsObject_false_noSuchKey() {
        NoSuchKeyException ex = (NoSuchKeyException) NoSuchKeyException.builder().build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(ex);

        boolean result = engine.existsObject(KEY);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("existsObject - headObject 抛出 SdkException 时为 false")
    void existsObject_false_sdkException() {
        SdkException sdkEx = S3Exception.builder().message("sdk failure").build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(sdkEx);

        boolean result = engine.existsObject(KEY);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("issueDirectUploadPolicy - 预签名成功时返回带 URL 和 headers 的策略")
    void issueDirectUploadPolicy_success() {
        PresignedPutObjectRequest signed = mock(PresignedPutObjectRequest.class);
        URL url = mock(URL.class);
        lenient().when(url.toString()).thenReturn("https://example.com/upload?signature=xxx");
        lenient().when(signed.url()).thenReturn(url);
        lenient().when(signed.signedHeaders())
                .thenReturn(Map.of("host", List.of("example.com"), "x-amz-acl", List.of("public-read")));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);

        var policy = engine.issueDirectUploadPolicy(KEY, 600);

        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.isFallback()).isFalse();
        assertThat(policy.getMethod()).isEqualTo("PUT");
        assertThat(policy.getUploadUrl()).startsWith("https://example.com/upload");
        assertThat(policy.getHeaders()).containsEntry("host", "example.com");
        assertThat(policy.getHeaders()).containsEntry("x-amz-acl", "public-read");
        assertThat(policy.getFormFields()).isEmpty();
        assertThat(policy.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(policy.getKey()).isEqualTo("base/test.txt");
        assertThat(policy.getExpireAt()).isNotNull();
    }

    @Test
    @DisplayName("issueDirectUploadPolicy - 预签名失败时降级为 fallback 策略")
    void issueDirectUploadPolicy_fallback() {
        doThrow(new RuntimeException("presign failure"))
                .when(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));

        var policy = engine.issueDirectUploadPolicy(KEY, 600);

        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.isFallback()).isTrue();
        assertThat(policy.getErrorMessage()).isNotBlank();
        assertThat(policy.getMethod()).isEqualTo("PUT");
        assertThat(policy.getUploadUrl()).isNotBlank();
        assertThat(policy.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(policy.getKey()).isEqualTo("base/test.txt");
        assertThat(policy.getExpireAt()).isNotNull();
    }
}
