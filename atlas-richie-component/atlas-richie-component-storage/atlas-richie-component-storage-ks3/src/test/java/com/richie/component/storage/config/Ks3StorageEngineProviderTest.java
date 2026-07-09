/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

class Ks3StorageEngineProviderTest {

    private final Ks3StorageEngineProvider provider = new Ks3StorageEngineProvider();

    @Test
    void supportedEngineType_shouldReturnKsyunKs3() {
        assertThat(provider.supportedEngineType()).isEqualTo(StorageEngineEnum.KSYUN_KS3);
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
        properties.getObject().setEndpoint("ks3-cn-beijing.ksyuncs.com");
        properties.getObject().setAccessKeyId("ak");
        properties.getObject().setAccessKeySecret("sk");
        properties.getObject().setBucketName("bucket");
        return properties;
    }
}
