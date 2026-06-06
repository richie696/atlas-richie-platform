package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.config.StorageProperties;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractObjectStorageEngineTest {

    @Mock
    private StorageTypeConverter converter;

    private StorageProperties properties;
    private TestEngine engine;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        ObjectConfig object = new ObjectConfig();
        object.setBucketName("bucket");
        object.setEndpoint("cdn.example.com");
        object.setBasePath("base");
        object.setStorageType(StorageTypeEnum.STANDARD);
        properties.setObject(object);
        engine = new TestEngine(properties, converter);
    }

    @Test
    void getRealPath_delegatesToObjectStorageKeys() {
        assertThat(engine.exposeRealPath("k")).isEqualTo("base/k");
    }

    @Test
    void getStorageClass_usesConverter() {
        when(converter.convertToEngineType(StorageTypeEnum.STANDARD)).thenReturn("STANDARD");
        assertThat(engine.exposeStorageClass()).isEqualTo("STANDARD");
    }

    @Test
    void buildPublicObjectUrl_withHttpsEndpoint() {
        properties.getObject().setEndpoint("https://s3.example.com");
        assertThat(engine.exposePublicUrl("obj.txt"))
                .isEqualTo("https://bucket.s3.example.com/base/obj.txt");
    }

    @Test
    void issueDirectUploadPolicy_returnsFallbackPolicy() {
        var policy = engine.issueDirectUploadPolicy("doc.txt", 30);
        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.isFallback()).isTrue();
        assertThat(policy.getExpireAt()).isNotNull();
    }

    @Test
    void putData_map_successDelegatesToPutObject() {
        var response = engine.putData("data.json", Map.of("a", 1));
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getKey()).isEqualTo("base/data.json");
    }

    @Test
    void objectConfig_andBucketName_shouldExposeProperties() {
        assertThat(engine.objectConfig()).isSameAs(properties.getObject());
        assertThat(engine.getBucketName()).isEqualTo("bucket");
        assertThat(engine.getResourceKey("a.txt")).isEqualTo("cdn.example.com/a.txt");
    }

    @Test
    void buildPublicObjectUrl_blankEndpoint_returnsRealKey() {
        properties.getObject().setEndpoint("");
        assertThat(engine.exposePublicUrl("only.txt")).isEqualTo("base/only.txt");
    }

    @Test
    void buildPublicObjectUrl_endpointContainsBucket_usesEndpointDirectly() {
        properties.getObject().setEndpoint("bucket.s3.example.com");
        assertThat(engine.exposePublicUrl("f.txt")).isEqualTo("https://bucket.s3.example.com/base/f.txt");
    }

    @Test
    void getStorageClass_nullConverterResult_returnsEmptyString() {
        when(converter.convertToEngineType(StorageTypeEnum.STANDARD)).thenReturn(null);
        assertThat(engine.exposeStorageClass()).isEmpty();
    }

    @Test
    void getAcl_withWorkingConverter_returnsValue() {
        var aclConverter = new com.richie.component.storage.converter.AclTypeConverter<String>() {
            @Override
            public String convertToEngineAcl(AclTypeEnum aclType) {
                return "private";
            }

            @Override
            public StorageEngineEnum getSupportedEngine() {
                return StorageEngineEnum.AWS_S3;
            }
        };
        ReflectionTestUtils.setField(engine, "aclTypeConverter", aclConverter);
        properties.getObject().setAcl(AclTypeEnum.PRIVATE);
        assertThat((String) engine.exposeAcl()).isEqualTo("private");
    }

    @Test
    void putData_collection_andObject_delegateToPutObject() {
        assertThat(engine.putData("c.json", java.util.List.of(1)).isSuccess()).isTrue();
        assertThat(engine.putData("o.json", Map.of("x", 1)).isSuccess()).isTrue();
    }

    @Test
    void putData_whenSerializationFails_returnsErrorResponse() {
        JsonUtils jsonUtils = mock(JsonUtils.class);
        try (MockedStatic<JsonUtils> mocked = mockStatic(JsonUtils.class)) {
            mocked.when(JsonUtils::getInstance).thenReturn(jsonUtils);
            when(jsonUtils.serializeBytes(any())).thenReturn(null);

            UploadResponse mapResult = engine.putData("a.json", Map.of("k", "v"));
            UploadResponse listResult = engine.putData("b.json", java.util.List.of(1));
            UploadResponse objResult = engine.putData("c.json", Map.of("x", 1));

            assertThat(mapResult.isSuccess()).isFalse();
            assertThat(listResult.isSuccess()).isFalse();
            assertThat(objResult.isSuccess()).isFalse();
        }
    }

    @Test
    void getAcl_withoutAclType_returnsNull() {
        properties.getObject().setAcl(null);
        assertThat((Object) engine.exposeAcl()).isNull();
    }

    @Test
    void getAcl_withoutConverter_returnsNull() {
        properties.getObject().setAcl(AclTypeEnum.PRIVATE);
        assertThat((Object) engine.exposeAcl()).isNull();
    }

    @Test
    void buildPublicObjectUrl_httpEndpoint_normalizesScheme() {
        properties.getObject().setEndpoint("http://files.example.com");
        assertThat(engine.exposePublicUrl("x.bin"))
                .isEqualTo("https://bucket.files.example.com/base/x.bin");
    }

    @Test
    void issueDirectUploadPolicy_enforcesMinimumExpireSeconds() {
        var policy = engine.issueDirectUploadPolicy("k", 10);
        assertThat(policy.getExpireAt()).isAfter(java.time.OffsetDateTime.now().plusSeconds(59));
    }

    @Test
    void getAcl_withFailingConverter_returnsNull() {
        var aclConverter = new com.richie.component.storage.converter.AclTypeConverter<String>() {
            @Override
            public String convertToEngineAcl(AclTypeEnum aclType) {
                throw new RuntimeException("boom");
            }
            @Override
            public StorageEngineEnum getSupportedEngine() {
                return StorageEngineEnum.AWS_S3;
            }
        };
        ReflectionTestUtils.setField(engine, "aclTypeConverter", aclConverter);
        properties.getObject().setAcl(AclTypeEnum.PRIVATE);
        assertThat((Object) engine.exposeAcl()).isNull();
    }

    private static final class TestEngine extends AbstractObjectStorageEngine<Object> {
        TestEngine(StorageProperties properties, StorageTypeConverter converter) {
            super(properties, converter);
        }

        String exposeRealPath(String key) {
            return getRealPath(key);
        }

        String exposeStorageClass() {
            return getStorageClass();
        }

        String exposePublicUrl(String key) {
            return buildPublicObjectUrl(getRealPath(key));
        }

        <T> T exposeAcl() {
            return getAcl();
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.InputStream inputStream) {
            return com.richie.component.storage.bean.UploadResponse.builder()
                    .success(true).key(getRealPath(key)).build();
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.File file) {
            return putObject(key, new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.File file,
                com.richie.component.storage.bean.image.ImageOptions options) {
            return putObject(key, file);
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.InputStream inputStream,
                com.richie.component.storage.bean.image.ImageOptions options) {
            return putObject(key, inputStream);
        }

        @Override
        public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(String key,
                tools.jackson.core.type.TypeReference<T> typeReference) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(String key, java.io.File targetPath,
                boolean returnData) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(String key,
                String targetPath, boolean returnData) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public boolean existsObject(String key) {
            return false;
        }
    }
}
