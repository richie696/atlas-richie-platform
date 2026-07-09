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
package com.richie.component.storage.core.integration;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.support.StorageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@StorageIntegrationTest
@TestPropertySource(properties = {
        "platform.component.storage.object.bucket-name=it-bucket",
        "platform.component.storage.object.endpoint=memory.local",
        "platform.component.storage.object.base-path=it"
})
class StoragePropertiesLoadIT {

    @Autowired
    private StorageProperties storageProperties;

    @Test
    void storageProperties_shouldBindFromClasspathConfig() {
        assertThat(storageProperties.getObject().getBucketName()).isEqualTo("it-bucket");
        assertThat(storageProperties.getObject().getEndpoint()).isEqualTo("memory.local");
        assertThat(storageProperties.getObject().getBasePath()).isEqualTo("it");
    }
}
