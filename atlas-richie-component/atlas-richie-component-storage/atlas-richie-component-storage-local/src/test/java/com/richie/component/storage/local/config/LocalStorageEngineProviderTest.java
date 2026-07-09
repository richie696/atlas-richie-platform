/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.local.config;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.enums.StorageEngineEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageEngineProviderTest {

    private final LocalStorageEngineProvider provider = new LocalStorageEngineProvider();

    @Test
    void supportedEngineType_shouldReturnLocal() {
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.LOCAL);
    }

    @Test
    void validate_withValidConfig_shouldNotThrow() {
        assertThatCode(() -> provider.validate(validProperties()))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_withNullLocalConfig_shouldThrow() {
        StorageProperties properties = new StorageProperties();
        properties.setLocal(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本地存储配置");
    }

    @Test
    void validate_withNullPath_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getLocal().setPath(null);

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void validate_withBlankPath_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getLocal().setPath("");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void validate_withWhitespacePath_shouldThrow() {
        StorageProperties properties = validProperties();
        properties.getLocal().setPath("   ");

        assertThatThrownBy(() -> provider.validate(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    private StorageProperties validProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setPath("/tmp/storage");
        return properties;
    }
}
