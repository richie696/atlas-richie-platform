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
package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.config.support.VectorIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@VectorIntegrationTest
@TestPropertySource(properties = {
        "platform.component.vector.default-index=it-documents",
        "platform.component.vector.indexes.it-documents.name=it-documents",
        "platform.component.vector.indexes.it-documents.dimension=3"
})
class VectorPropertiesLoadIT {

    @Autowired
    private VectorProperties vectorProperties;

    @Test
    void vectorProperties_shouldBindIndexesFromConfig() {
        assertThat(vectorProperties.getDefaultIndex()).isEqualTo("it-documents");
        assertThat(vectorProperties.getIndexes()).containsKey("it-documents");
        assertThat(vectorProperties.getIndexes().get("it-documents").getDimension()).isEqualTo(3);
    }
}
