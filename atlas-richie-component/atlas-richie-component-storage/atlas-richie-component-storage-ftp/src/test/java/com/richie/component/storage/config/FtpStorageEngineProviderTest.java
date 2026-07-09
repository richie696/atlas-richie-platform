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
