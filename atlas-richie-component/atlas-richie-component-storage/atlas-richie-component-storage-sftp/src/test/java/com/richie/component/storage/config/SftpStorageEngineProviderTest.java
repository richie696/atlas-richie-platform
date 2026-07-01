package com.richie.component.storage.config;

import com.richie.component.storage.enums.StorageEngineEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpStorageEngineProviderTest {

    private final SftpStorageEngineProvider provider = new SftpStorageEngineProvider();

    @Test
    void supportedEngineType_shouldReturnSftp() {
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.SFTP);
    }

    @Test
    void validate_withValidConfig_shouldNotThrow() {
        assertThatCode(() -> provider.validate(validProperties()))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_withNullSftpConfig_shouldThrow() {
        StorageProperties properties = new StorageProperties();
        properties.setSftp(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SFTP 配置");
    }

    @Test
    void validate_withBlankHost_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getSftp().setHost("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void validate_withBlankUsername_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getSftp().setUsername("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test
    void validate_withBlankPassword_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getSftp().setPassword("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void destroy_withNullSshClient_shouldNotThrow() {
        assertThatCode(() -> provider.destroy(null)).doesNotThrowAnyException();
    }

    private StorageProperties validProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getSftp().setHost("sftp.example.com");
        properties.getSftp().setUsername("user");
        properties.getSftp().setPassword("pass");
        return properties;
    }
}
