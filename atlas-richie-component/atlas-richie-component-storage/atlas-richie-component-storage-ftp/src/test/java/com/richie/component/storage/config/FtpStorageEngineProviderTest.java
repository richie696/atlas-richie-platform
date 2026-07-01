package com.richie.component.storage.config;

import com.richie.component.storage.enums.StorageEngineEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FtpStorageEngineProviderTest {

    private final FtpStorageEngineProvider provider = new FtpStorageEngineProvider();

    @Test
    void supportedEngineType_shouldReturnFtp() {
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.FTP);
    }

    @Test
    void validate_withValidConfig_shouldNotThrow() {
        assertThatCode(() -> provider.validate(validProperties()))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_withNullFtpConfig_shouldThrow() {
        StorageProperties properties = new StorageProperties();
        properties.setFtp(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FTP 配置");
    }

    @Test
    void validate_withBlankHost_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getFtp().setHost("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void validate_withNullHost_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getFtp().setHost(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void destroy_shouldNotThrow() {
        assertThatCode(() -> provider.destroy(null)).doesNotThrowAnyException();
    }

    private StorageProperties validProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getFtp().setHost("ftp.example.com");
        properties.getFtp().setPort(21);
        properties.getFtp().setUsername("user");
        properties.getFtp().setPassword("pass");
        return properties;
    }
}
