package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.enums.StorageEngineEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MinioStorageEngineProvider 单元测试
 * <p>
 * 覆盖 supportedEngineType、validate 全部分支、destroy 不抛异常、afterPropertiesSet。
 */
class MinioStorageEngineProviderTest {

    private final MinioStorageEngineProvider provider = new MinioStorageEngineProvider();

    @Test
    void supportedEngineType_shouldReturnMinio() {
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.MINIO);
    }

    @Test
    void validate_withValidConfig_shouldNotThrow() {
        StorageProperties properties = new StorageProperties();
        properties.getObject().setEndpoint("http://minio:9000");
        properties.getObject().setAccessKeyId("ak");
        properties.getObject().setAccessKeySecret("sk");
        properties.getObject().setBucketName("bucket");

        assertThatCode(() -> provider.validate(properties)).doesNotThrowAnyException();
    }

    @Test
    void validate_withNullObjectConfig_shouldThrow() {
        StorageProperties properties = new StorageProperties();
        properties.setObject(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象存储配置");
    }

    @Test
    void validate_withBlankEndpoint_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getObject().setEndpoint("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void validate_withNullEndpoint_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getObject().setEndpoint(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void validate_withWhitespaceAccessKeyId_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getObject().setAccessKeyId("   ");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKeyId");
    }

    @Test
    void validate_withBlankAccessKeySecret_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getObject().setAccessKeySecret("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKeySecret");
    }

    @Test
    void validate_withBlankBucketName_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getObject().setBucketName("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucketName");
    }

    @Test
    void destroy_shouldNotThrow() {
        assertThatCode(() -> provider.destroy(null))
                .doesNotThrowAnyException();
    }

    @Test
    void afterPropertiesSet_withNullEngine_shouldNotThrow() {
        assertThatCode(() -> provider.afterPropertiesSet(null))
                .doesNotThrowAnyException();
    }

    private StorageProperties validProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getObject().setEndpoint("http://minio:9000");
        properties.getObject().setAccessKeyId("ak");
        properties.getObject().setAccessKeySecret("sk");
        properties.getObject().setBucketName("bucket");
        return properties;
    }
}
