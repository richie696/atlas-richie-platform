package com.richie.component.storage.config;

import com.richie.component.storage.enums.StorageEngineEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TosStorageEngineProviderTest {

    private final TosStorageEngineProvider provider = new TosStorageEngineProvider();

    @Test
    void supportedEngineType_shouldReturnVolcengineTos() {
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.VOLCENGINE_TOS);
    }

    @Test
    void validate_withValidConfig_shouldNotThrow() {
        assertThatCode(() -> provider.validate(validProperties()))
                .doesNotThrowAnyException();
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
    void validate_withBlankRegion_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getObject().setRegion("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");
    }

    @Test
    void validate_withBlankAccessKeyId_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getObject().setAccessKeyId("");

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
        assertThatCode(() -> provider.destroy(null)).doesNotThrowAnyException();
    }

    private StorageProperties validProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getObject().setEndpoint("tos-cn-beijing.volces.com");
        properties.getObject().setRegion("cn-beijing");
        properties.getObject().setAccessKeyId("ak");
        properties.getObject().setAccessKeySecret("sk");
        properties.getObject().setBucketName("bucket");
        return properties;
    }
}
