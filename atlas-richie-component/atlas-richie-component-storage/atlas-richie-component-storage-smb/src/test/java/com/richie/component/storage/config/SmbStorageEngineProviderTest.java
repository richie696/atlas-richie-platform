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

class SmbStorageEngineProviderTest {

    private final SmbStorageEngineProvider provider = new SmbStorageEngineProvider();

    @Test
    void supportedEngineType_shouldReturnSmb() {
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.SMB);
    }

    @Test
    void validate_withValidConfig_shouldNotThrow() {
        assertThatCode(() -> provider.validate(validProperties()))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_withNullSmbConfig_shouldThrow() {
        StorageProperties properties = new StorageProperties();
        properties.setSmb3(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SMB 配置");
    }

    @Test
    void validate_withBlankUsername_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getSmb3().setUsername("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test
    void validate_withBlankPassword_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getSmb3().setPassword("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void validate_withBlankDomain_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getSmb3().setDomain("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domain");
    }

    @Test
    void destroy_shouldNotThrow() {
        assertThatCode(() -> provider.destroy(null)).doesNotThrowAnyException();
    }

    private StorageProperties validProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getSmb3().setUsername("user");
        properties.getSmb3().setPassword("pass");
        properties.getSmb3().setDomain("WORKGROUP");
        return properties;
    }
}
